package edu.course.lab.cms.taxonomy.service;

import edu.course.lab.cms.taxonomy.model.CategoryNode;
import java.util.List;
import java.util.Optional;

/** 交给模块 C 的分类查询接口：分类 ID、能否关联发布、按父分类取有效子孙。 */
public interface CategoryQueryService {
    Optional<CategorySnapshot> findById(long categoryId);

    /** 分类及其全部祖先都存在且启用时才可以作为发布目标。 */
    boolean isPublishable(long categoryId);

    /** 取有效子孙 ID（含自身）；parentId 为 0 时返回全部有效分类。 */
    List<Long> effectiveCategoryIds(long parentId);

    List<CategoryNode> tree(boolean includeDisabled);
}
