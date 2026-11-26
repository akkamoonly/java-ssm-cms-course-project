package edu.course.lab.cms.taxonomy.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.audit.AuditConstants;
import edu.course.lab.cms.platform.audit.AuditService;
import edu.course.lab.cms.taxonomy.mapper.CategoryMapper;
import edu.course.lab.cms.taxonomy.model.Category;
import edu.course.lab.cms.taxonomy.model.CategoryNode;
import edu.course.lab.cms.taxonomy.support.CategoryTree;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService implements CategoryAdminService {
    private static final int MAX_NAME_LENGTH = 64;
    private static final int SORT_STEP = 10;

    private final CategoryMapper mapper;
    private final AuditService audit;
    private final ObjectProvider<CategoryArticleReference> articleReference;

    public CategoryService(CategoryMapper mapper, AuditService audit,
                           ObjectProvider<CategoryArticleReference> articleReference) {
        this.mapper = mapper;
        this.audit = audit;
        this.articleReference = articleReference;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CategorySnapshot> findById(long categoryId) {
        return Optional.ofNullable(mapper.selectById(categoryId)).map(CategoryService::snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPublishable(long categoryId) {
        return CategoryTree.pathEnabled(CategoryTree.index(mapper.findAll()), categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> effectiveCategoryIds(long parentId) {
        return CategoryTree.effectiveSubtreeIds(mapper.findAll(), parentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryNode> tree(boolean includeDisabled) {
        return CategoryTree.build(mapper.findAll(), includeDisabled);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryView> list(boolean includeDisabled) {
        return mapper.findAll().stream()
                .filter(category -> includeDisabled || category.enabledValue())
                .map(CategoryService::view)
                .toList();
    }

    @Override
    @Transactional
    public CategoryView create(long actorId, long parentId, String rawName, int sortOrder, boolean enabled) {
        String name = normalizeName(rawName);
        requireParentExists(parentId);
        requireSiblingNameFree(parentId, name, 0L);
        var category = new Category();
        category.setParentId(parentId);
        category.setName(name);
        category.setSortOrder(sortOrder);
        category.setEnabled(enabled);
        try {
            mapper.insert(category);
        } catch (DuplicateKeyException exception) {
            throw duplicateName();
        }
        return view(category);
    }

    @Override
    @Transactional
    public CategoryView update(long actorId, long categoryId, Long rawParentId, String rawName, Integer sortOrder) {
        var category = requireCategory(categoryId);
        Map<Long, Category> byId = CategoryTree.index(mapper.findAll());
        long parentId = rawParentId == null
                ? CategoryTree.normalizeParentId(category.getParentId())
                : CategoryTree.normalizeParentId(rawParentId);
        String name = rawName == null ? category.getName() : normalizeName(rawName);
        if (parentId != CategoryTree.normalizeParentId(category.getParentId())) {
            requireParentExists(parentId);
            if (CategoryTree.wouldCreateCycle(byId, categoryId, parentId)) {
                throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_PARENT_CYCLE",
                        "新父级不能是自身或自身的子孙");
            }
        }
        requireSiblingNameFree(parentId, name, categoryId);
        category.setParentId(parentId);
        category.setName(name);
        if (sortOrder != null) {
            category.setSortOrder(sortOrder);
        }
        try {
            mapper.updateById(category);
        } catch (DuplicateKeyException exception) {
            throw duplicateName();
        }
        return view(category);
    }

    @Override
    @Transactional
    public void delete(long actorId, long categoryId) {
        requireCategory(categoryId);
        if (mapper.countChildren(categoryId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_HAS_CHILDREN", "请先删除或迁移子分类");
        }
        var reference = articleReference.getIfAvailable();
        if (reference != null && reference.countByCategoryId(categoryId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_IN_USE", "该分类已被文章引用，不能删除");
        }
        try {
            mapper.deleteById(categoryId);
        } catch (DataIntegrityViolationException exception) {
            // cms_article.category_id 外键兜底：C 模块尚未实现引用检查时由数据库拦截。
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_IN_USE", "该分类已被文章引用，不能删除");
        }
    }

    /** 启停分类，成功记录与业务变更同事务，动作使用平台审计常量。 */
    @Override
    @Transactional
    public CategoryView setEnabled(long actorId, long categoryId, boolean enabled) {
        var category = requireCategory(categoryId);
        if (category.enabledValue() == enabled) {
            return view(category);
        }
        // 以原状态作联合条件，避免重复或并发提交把状态改乱。
        int updated = mapper.update(Wrappers.<Category>lambdaUpdate()
                .eq(Category::getId, categoryId)
                .eq(Category::getEnabled, !enabled)
                .set(Category::getEnabled, enabled)
                .set(Category::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_STATE_CONFLICT", "分类状态已被并发修改");
        }
        audit.recordSuccess(actorId, enabled ? AuditConstants.ACTION_ENABLE_CATEGORY
                : AuditConstants.ACTION_DISABLE_CATEGORY, AuditConstants.TARGET_CATEGORY, categoryId);
        var refreshed = mapper.selectById(categoryId);
        return view(refreshed == null ? category : refreshed);
    }

    /** 同一父级下的批量排序，按传入顺序以 10 为步长重排 sort_order。 */
    @Override
    @Transactional
    public List<CategoryView> reorder(long actorId, long parentId, List<Long> categoryIds) {
        requireParentExists(parentId);
        List<Long> ids = categoryIds.stream().distinct().toList();
        if (ids.size() != categoryIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_SORT_INVALID", "排序列表不能包含重复分类");
        }
        Map<Long, Category> byId = CategoryTree.index(mapper.findAll());
        for (Long id : ids) {
            var category = byId.get(id);
            if (category == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "分类不存在：" + id);
            }
            if (CategoryTree.normalizeParentId(category.getParentId()) != parentId) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_SORT_INVALID",
                        "只能对同一父级下的分类排序：" + id);
            }
        }
        List<CategoryView> result = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            var category = byId.get(ids.get(index));
            category.setSortOrder((index + 1) * SORT_STEP);
            mapper.updateById(category);
            result.add(view(category));
        }
        return result;
    }

    private Category requireCategory(long categoryId) {
        var category = mapper.selectById(categoryId);
        if (category == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "分类不存在");
        }
        return category;
    }

    private void requireParentExists(long parentId) {
        if (parentId == CategoryTree.ROOT_PARENT_ID) {
            return;
        }
        if (parentId < CategoryTree.ROOT_PARENT_ID) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_PARENT_INVALID", "父分类 ID 无效");
        }
        if (mapper.selectById(parentId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_PARENT_NOT_FOUND", "父分类不存在");
        }
    }

    private void requireSiblingNameFree(long parentId, String name, long excludeId) {
        if (mapper.countSiblingName(parentId, name, excludeId) > 0) {
            throw duplicateName();
        }
    }

    private static ApiException duplicateName() {
        return new ApiException(HttpStatus.CONFLICT, "CATEGORY_NAME_DUPLICATED", "同级分类名称已存在");
    }

    private static String normalizeName(String rawName) {
        String name = Objects.requireNonNullElse(rawName, "").strip();
        if (name.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_NAME_INVALID", "分类名称不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_NAME_INVALID",
                    "分类名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        return name;
    }

    private static CategorySnapshot snapshot(Category category) {
        return new CategorySnapshot(category.getId(), CategoryTree.normalizeParentId(category.getParentId()),
                category.getName(), sortOrderOf(category), category.enabledValue());
    }

    private static CategoryView view(Category category) {
        return new CategoryView(category.getId(), CategoryTree.normalizeParentId(category.getParentId()),
                category.getName(), sortOrderOf(category), category.enabledValue(),
                category.getCreatedAt(), category.getUpdatedAt());
    }

    private static int sortOrderOf(Category category) {
        return category.getSortOrder() == null ? 0 : category.getSortOrder();
    }
}



