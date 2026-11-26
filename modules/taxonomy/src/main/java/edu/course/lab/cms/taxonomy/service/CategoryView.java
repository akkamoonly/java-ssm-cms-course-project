package edu.course.lab.cms.taxonomy.service;

import java.time.LocalDateTime;

/** 分类的后台视图，包含启停与时间字段，供管理端列表使用。 */
public record CategoryView(long id, long parentId, String name, int sortOrder, boolean enabled,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
}
