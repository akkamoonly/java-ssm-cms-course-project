package edu.course.lab.cms.taxonomy;

import edu.course.lab.cms.app.PersistenceConfig;
import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.audit.AuditConstants;
import edu.course.lab.cms.taxonomy.model.CategoryNode;
import edu.course.lab.cms.taxonomy.service.CategoryAdminService;
import edu.course.lab.cms.taxonomy.service.CategoryView;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "CMS_VERIFY_MYSQL", matches = "true")
class CategoryMySqlTest {
    private static final long ACTOR_ID = 9001L;

    @Test
    void treeCycleReferenceAndAuditRunAgainstRealMySql() {
        try (var context = new AnnotationConfigApplicationContext(PersistenceConfig.class)) {
            var service = context.getBean(CategoryAdminService.class);
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            String tag = "验收-" + System.nanoTime();
            boolean articleTableCreated = false;
            try {
                // 1. 多级分类与同级排序
                var root = service.create(ACTOR_ID, 0, tag + "-制度", 10, true);
                var childLate = service.create(ACTOR_ID, root.id(), tag + "-子乙", 20, true);
                var childEarly = service.create(ACTOR_ID, root.id(), tag + "-子甲", 10, true);
                var grand = service.create(ACTOR_ID, childLate.id(), tag + "-孙", 10, true);

                var rootNode = findRoot(service.tree(true), root.id());
                assertEquals(List.of(childEarly.id(), childLate.id()),
                        rootNode.children().stream().map(CategoryNode::id).toList(), "同级按 sortOrder 升序");
                assertEquals(1, rootNode.children().get(1).children().size(), "三级分类挂在正确的父级下");
                assertEquals(root.id(), findRoot(service.tree(false), root.id()).id(), "有效分类在读者树中可见");

                // 2. 循环父级拦截
                var cycle = assertThrows(ApiException.class,
                        () -> service.update(ACTOR_ID, root.id(), grand.id(), null, null));
                assertEquals("CATEGORY_PARENT_CYCLE", cycle.code());
                var self = assertThrows(ApiException.class,
                        () -> service.update(ACTOR_ID, childEarly.id(), childEarly.id(), null, null));
                assertEquals("CATEGORY_PARENT_CYCLE", self.code());

                // 3. 同级重名（含首尾空格）
                var duplicated = assertThrows(ApiException.class,
                        () -> service.create(ACTOR_ID, root.id(), "  " + tag + "-子甲  ", 30, true));
                assertEquals("CATEGORY_NAME_DUPLICATED", duplicated.code());
                var parentMissing = assertThrows(ApiException.class,
                        () -> service.create(ACTOR_ID, 999_999L, tag + "-孤儿", 10, true));
                assertEquals("CATEGORY_PARENT_NOT_FOUND", parentMissing.code());

                // 4. 排序接口按传入顺序重排，跨父级和重复 ID 被拒绝
                var sorted = service.reorder(ACTOR_ID, root.id(), List.of(childLate.id(), childEarly.id()));
                assertEquals(List.of(childLate.id(), childEarly.id()),
                        sorted.stream().map(CategoryView::id).toList());
                assertEquals(10, sorted.get(0).sortOrder());
                assertEquals(20, sorted.get(1).sortOrder());
                assertEquals(List.of(childLate.id(), childEarly.id()),
                        findRoot(service.tree(true), root.id()).children().stream()
                                .map(CategoryNode::id).toList(), "树查询跟随新的排序结果");

                assertEquals("CATEGORY_SORT_INVALID", assertThrows(ApiException.class,
                        () -> service.reorder(ACTOR_ID, root.id(), List.of(childLate.id(), grand.id()))).code());
                assertEquals("CATEGORY_SORT_INVALID", assertThrows(ApiException.class,
                        () -> service.reorder(ACTOR_ID, root.id(),
                                List.of(childLate.id(), childLate.id()))).code());

                // 5. 含子分类的删除被拒绝
                assertEquals("CATEGORY_HAS_CHILDREN",
                        assertThrows(ApiException.class, () -> service.delete(ACTOR_ID, root.id())).code());

                // 6. 启停：发布可用性变化 + 审计记录
                assertEquals(true, service.isPublishable(grand.id()));
                service.setEnabled(ACTOR_ID, grand.id(), false);
                assertFalse(service.isPublishable(grand.id()), "祖先停用后子分类不可发布");
                assertFalse(service.effectiveCategoryIds(root.id()).contains(grand.id()),
                        "停用节点不出现在有效子孙集合");
                assertEquals(1, countAudit(jdbc, AuditConstants.ACTION_DISABLE_CATEGORY, grand.id()));
                service.setEnabled(ACTOR_ID, grand.id(), true);
                assertEquals(1, countAudit(jdbc, AuditConstants.ACTION_ENABLE_CATEGORY, grand.id()));
                assertEquals(1L, service.effectiveCategoryIds(root.id()).stream()
                        .filter(id -> id == grand.id()).count());

                // 7. 文章引用由外键兜底
                if (!tableExists(jdbc, "cms_article")) {
                    jdbc.execute("""
                            CREATE TABLE cms_article (
                                id BIGINT NOT NULL AUTO_INCREMENT,
                                category_id BIGINT NOT NULL,
                                title VARCHAR(200) NOT NULL,
                                PRIMARY KEY (id),
                                CONSTRAINT fk_article_category FOREIGN KEY (category_id) REFERENCES cms_category (id)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """);
                    articleTableCreated = true;
                }
                jdbc.update("INSERT INTO cms_article (category_id, title) VALUES (?, ?)", childEarly.id(), tag);
                assertEquals("CATEGORY_IN_USE",
                        assertThrows(ApiException.class, () -> service.delete(ACTOR_ID, childEarly.id())).code());
                jdbc.update("DELETE FROM cms_article WHERE category_id = ?", childEarly.id());
                service.delete(ACTOR_ID, childEarly.id());
                assertEquals(1, countAudit(jdbc, AuditConstants.ACTION_DISABLE_CATEGORY, grand.id()));
            } finally {
                if (articleTableCreated || tableExists(jdbc, "cms_article")) {
                    jdbc.update("DELETE FROM cms_article WHERE title LIKE ?", "验收-%");
                }
                if (articleTableCreated) {
                    jdbc.execute("DROP TABLE IF EXISTS cms_article");
                }
                // 自增 ID 沿创建顺序递增，按 ID 倒序删除可先删子孙再删父级。
                jdbc.update("DELETE FROM cms_category WHERE name LIKE ? ORDER BY id DESC", "验收-%");
                jdbc.update("DELETE FROM cms_operation_log WHERE target_type = ? AND actor_id = ?",
                        AuditConstants.TARGET_CATEGORY, ACTOR_ID);
            }
        }
    }

    private static CategoryNode findRoot(List<CategoryNode> nodes, long id) {
        return nodes.stream().filter(node -> node.id() == id).findFirst()
                .orElseThrow(() -> new AssertionError("树中缺少根分类 " + id));
    }

    private static long countAudit(JdbcTemplate jdbc, String action, long categoryId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cms_operation_log
                WHERE target_type = ? AND action = ? AND target_id = ? AND result = 'SUCCESS'
                """, Long.class, AuditConstants.TARGET_CATEGORY, action, categoryId);
        return count == null ? 0 : count;
    }

    private static boolean tableExists(JdbcTemplate jdbc, String tableName) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """, Long.class, tableName);
        return count != null && count > 0;
    }

    @Test
    void seededCategoriesAreReadable() {
        try (var context = new AnnotationConfigApplicationContext(PersistenceConfig.class)) {
            var service = context.getBean(CategoryAdminService.class);
            var tree = service.tree(false);
            assertTrue(tree.stream().anyMatch(node -> node.name().equals("安全制度")
                    || node.name().equals("培训指引") || node.name().equals("安全通报")),
                    "初始化 SQL 应提供三个根分类，请先执行 db/init/002-taxonomy.sql");
        }
    }
}

