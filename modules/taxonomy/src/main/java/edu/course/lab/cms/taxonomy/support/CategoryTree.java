package edu.course.lab.cms.taxonomy.support;

import edu.course.lab.cms.taxonomy.model.Category;
import edu.course.lab.cms.taxonomy.model.CategoryNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分类树的纯内存逻辑：组树、层级校验和有效路径判断，不依赖数据库，便于单测。
 *
 * <p>有效路径（发布与读者可见）的定义：节点存在、自身 enabled，且每一级祖先都存在且 enabled。
 */
public final class CategoryTree {
    public static final long ROOT_PARENT_ID = 0L;
    private static final int MAX_DEPTH = 64;
    private static final Comparator<Category> SIBLING_ORDER =
            Comparator.comparing(Category::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Category::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private CategoryTree() {
    }

    public static Map<Long, Category> index(List<Category> categories) {
        Map<Long, Category> byId = new LinkedHashMap<>();
        for (Category category : categories) {
            byId.put(category.getId(), category);
        }
        return byId;
    }

    /** 归一化 parentId：null 视为根 0。 */
    public static long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_PARENT_ID : parentId;
    }

    /** 先批量取数再按 parentId 组树，同级按 sortOrder、id 稳定排序。 */
    public static List<CategoryNode> build(List<Category> categories, boolean includeDisabled) {
        Map<Long, Category> byId = index(categories);
        Map<Long, List<Category>> childrenOf = new HashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category category : categories) {
            if (!includeDisabled && !pathEnabled(byId, category.getId())) {
                continue;
            }
            long parentId = normalizeParentId(category.getParentId());
            if (parentId == ROOT_PARENT_ID || !byId.containsKey(parentId)) {
                // 父节点不存在的悬挂节点按根处理，避免整棵子树丢失。
                roots.add(category);
                continue;
            }
            if (!includeDisabled && !byId.get(parentId).enabledValue()) {
                // 父节点停用时，子节点不进入读者树。
                continue;
            }
            childrenOf.computeIfAbsent(parentId, key -> new ArrayList<>()).add(category);
        }
        roots.sort(SIBLING_ORDER);
        return roots.stream().map(category -> toNode(category, childrenOf)).toList();
    }

    private static CategoryNode toNode(Category category, Map<Long, List<Category>> childrenOf) {
        List<Category> children = new ArrayList<>(childrenOf.getOrDefault(category.getId(), List.of()));
        children.sort(SIBLING_ORDER);
        return new CategoryNode(category.getId(), normalizeParentId(category.getParentId()),
                category.getName(), sortOrderOf(category), category.enabledValue(),
                children.stream().map(child -> toNode(child, childrenOf)).toList());
    }

    private static int sortOrderOf(Category category) {
        return category.getSortOrder() == null ? 0 : category.getSortOrder();
    }

    /** 节点及其全部祖先是否都存在且启用。 */
    public static boolean pathEnabled(Map<Long, Category> byId, long categoryId) {
        Category category = byId.get(categoryId);
        if (category == null || !category.enabledValue()) {
            return false;
        }
        Set<Long> visited = new HashSet<>();
        long cursor = normalizeParentId(category.getParentId());
        int depth = 0;
        while (cursor != ROOT_PARENT_ID) {
            if (!visited.add(cursor) || depth++ > MAX_DEPTH) {
                return false;
            }
            Category parent = byId.get(cursor);
            if (parent == null || !parent.enabledValue()) {
                return false;
            }
            cursor = normalizeParentId(parent.getParentId());
        }
        return true;
    }

    /**
     * 把分类挂到新父级是否形成环：新父级不能是自己，也不能是自己的子孙。
     * 已存在的悬挂或超深链条同样按非法处理。
     */
    public static boolean wouldCreateCycle(Map<Long, Category> byId, long categoryId, long newParentId) {
        if (newParentId == ROOT_PARENT_ID) {
            return false;
        }
        if (newParentId == categoryId) {
            return true;
        }
        Set<Long> visited = new HashSet<>();
        long cursor = newParentId;
        int depth = 0;
        while (cursor != ROOT_PARENT_ID) {
            if (cursor == categoryId) {
                return true;
            }
            if (!visited.add(cursor) || depth++ > MAX_DEPTH) {
                return true;
            }
            Category parent = byId.get(cursor);
            if (parent == null) {
                return false;
            }
            cursor = normalizeParentId(parent.getParentId());
        }
        return false;
    }

    /** 按父分类取有效子孙 ID，含父分类自身；parentId 为 0 时返回全部有效分类。 */
    public static List<Long> effectiveSubtreeIds(List<Category> categories, long parentId) {
        Map<Long, Category> byId = index(categories);
        if (parentId != ROOT_PARENT_ID && !pathEnabled(byId, parentId)) {
            return List.of();
        }
        Map<Long, List<Long>> childrenOf = new HashMap<>();
        for (Category category : categories) {
            childrenOf.computeIfAbsent(normalizeParentId(category.getParentId()), key -> new ArrayList<>())
                    .add(category.getId());
        }
        List<Long> result = new ArrayList<>();
        if (parentId != ROOT_PARENT_ID) {
            collect(byId, childrenOf, parentId, result, new HashSet<>());
            return List.copyOf(result);
        }
        for (Category category : categories) {
            if (normalizeParentId(category.getParentId()) == ROOT_PARENT_ID && pathEnabled(byId, category.getId())) {
                collect(byId, childrenOf, category.getId(), result, new HashSet<>());
            }
        }
        return List.copyOf(result);
    }

    private static void collect(Map<Long, Category> byId, Map<Long, List<Long>> childrenOf, long categoryId,
                                List<Long> result, Set<Long> visited) {
        if (!visited.add(categoryId) || !pathEnabled(byId, categoryId)) {
            return;
        }
        result.add(categoryId);
        for (Long childId : childrenOf.getOrDefault(categoryId, List.of())) {
            collect(byId, childrenOf, childId, result, visited);
        }
    }
}

