package edu.course.lab.cms.taxonomy.service;

/** 分类的只读视图，供模块 C 等使用方读取，不暴露可变实体。 */
public record CategorySnapshot(long id, long parentId, String name, int sortOrder, boolean enabled) {
}
