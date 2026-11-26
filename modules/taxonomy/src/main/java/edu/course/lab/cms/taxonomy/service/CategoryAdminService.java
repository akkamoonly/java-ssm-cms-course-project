package edu.course.lab.cms.taxonomy.service;

import java.util.List;

/** 分类后台维护接口：增删改、排序和启停。 */
public interface CategoryAdminService extends CategoryQueryService {
    List<CategoryView> list(boolean includeDisabled);

    CategoryView create(long actorId, long parentId, String rawName, int sortOrder, boolean enabled);

    CategoryView update(long actorId, long categoryId, Long parentId, String rawName, Integer sortOrder);

    void delete(long actorId, long categoryId);

    CategoryView setEnabled(long actorId, long categoryId, boolean enabled);

    List<CategoryView> reorder(long actorId, long parentId, List<Long> categoryIds);
}
