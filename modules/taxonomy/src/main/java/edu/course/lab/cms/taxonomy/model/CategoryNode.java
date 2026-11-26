package edu.course.lab.cms.taxonomy.model;

import java.util.List;

/** 树形查询的返回节点，children 为空列表表示叶子。 */
public record CategoryNode(long id, long parentId, String name, int sortOrder, boolean enabled,
                           List<CategoryNode> children) {
    public CategoryNode {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
