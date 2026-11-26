package edu.course.lab.cms.taxonomy;

import edu.course.lab.cms.taxonomy.model.Category;
import edu.course.lab.cms.taxonomy.model.CategoryNode;
import edu.course.lab.cms.taxonomy.support.CategoryTree;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryTreeTest {
    @Test
    void buildsTreeWithStableSiblingOrder() {
        List<Category> rows = List.of(
                category(3, 0, "安全通报", 30),
                category(1, 0, "安全制度", 10),
                category(2, 0, "培训指引", 20),
                category(102, 1, "危险化学品管理", 20),
                category(101, 1, "实验室管理制度", 10),
                category(105, 3, "化学类通报", 10));

        List<CategoryNode> tree = CategoryTree.build(rows, true);

        assertEquals(List.of(1L, 2L, 3L), tree.stream().map(CategoryNode::id).toList());
        assertEquals(List.of(101L, 102L), tree.get(0).children().stream().map(CategoryNode::id).toList());
        assertEquals(List.of(105L), tree.get(2).children().stream().map(CategoryNode::id).toList());
        assertTrue(tree.get(1).children().isEmpty());
    }

    @Test
    void disabledParentHidesSubtreeForReadersButNotForAdmin() {
        List<Category> rows = List.of(
                category(1, 0, "安全制度", 10, false),
                category(101, 1, "实验室管理制度", 10, true),
                category(2, 0, "培训指引", 20, true));

        List<CategoryNode> readerTree = CategoryTree.build(rows, false);
        List<CategoryNode> adminTree = CategoryTree.build(rows, true);

        assertEquals(List.of(2L), readerTree.stream().map(CategoryNode::id).toList());
        assertEquals(List.of(1L, 2L), adminTree.stream().map(CategoryNode::id).toList());
        assertEquals(List.of(101L), adminTree.get(0).children().stream().map(CategoryNode::id).toList());
    }

    @Test
    void orphanNodeFallsBackToRootInsteadOfDisappearing() {
        List<Category> rows = List.of(
                category(1, 0, "安全制度", 10),
                category(9, 77, "悬挂节点", 10));

        assertEquals(List.of(1L, 9L), CategoryTree.build(rows, true).stream().map(CategoryNode::id).toList());
    }

    @Test
    void pathEnabledRequiresEveryAncestorEnabled() {
        List<Category> rows = List.of(
                category(1, 0, "安全制度", 10, true),
                category(2, 1, "实验室管理制度", 10, false),
                category(3, 2, "三级子分类", 10, true));
        Map<Long, Category> byId = CategoryTree.index(rows);

        assertTrue(CategoryTree.pathEnabled(byId, 1));
        assertFalse(CategoryTree.pathEnabled(byId, 2));
        assertFalse(CategoryTree.pathEnabled(byId, 3));
        assertFalse(CategoryTree.pathEnabled(byId, 404));
    }

    @Test
    void rejectsSelfAndAncestorAsNewParent() {
        List<Category> rows = List.of(
                category(1, 0, "安全制度", 10),
                category(2, 1, "实验室管理制度", 10),
                category(3, 2, "三级子分类", 10),
                category(4, 0, "培训指引", 20));
        Map<Long, Category> byId = CategoryTree.index(rows);

        assertTrue(CategoryTree.wouldCreateCycle(byId, 1, 1), "不能把自己设为父级");
        assertTrue(CategoryTree.wouldCreateCycle(byId, 1, 3), "不能把子孙设为父级");
        assertTrue(CategoryTree.wouldCreateCycle(byId, 2, 3));
        assertFalse(CategoryTree.wouldCreateCycle(byId, 3, 4), "换到另一棵树是合法的");
        assertFalse(CategoryTree.wouldCreateCycle(byId, 3, 0), "可以提升为根分类");
    }

    @Test
    void effectiveSubtreeIdsSkipDisabledBranches() {
        List<Category> rows = List.of(
                category(1, 0, "安全制度", 10),
                category(2, 1, "实验室管理制度", 10, false),
                category(3, 2, "三级子分类", 10),
                category(4, 1, "危险化学品管理", 20),
                category(5, 0, "培训指引", 20));

        assertEquals(List.of(1L, 4L), CategoryTree.effectiveSubtreeIds(rows, 1));
        assertEquals(List.of(), CategoryTree.effectiveSubtreeIds(rows, 2));
        assertEquals(List.of(1L, 4L, 5L), CategoryTree.effectiveSubtreeIds(rows, 0));
    }

    private static Category category(long id, long parentId, String name, int sortOrder) {
        return category(id, parentId, name, sortOrder, true);
    }

    private static Category category(long id, long parentId, String name, int sortOrder, boolean enabled) {
        var category = new Category();
        category.setId(id);
        category.setParentId(parentId);
        category.setName(name);
        category.setSortOrder(sortOrder);
        category.setEnabled(enabled);
        return category;
    }
}
