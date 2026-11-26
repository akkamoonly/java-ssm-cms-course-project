package edu.course.lab.cms.taxonomy.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 修改分类请求，字段为空表示保持不变；启停走单独接口以便审计。 */
public record CategoryUpdateRequest(
        @Size(max = 64, message = "分类名称不能超过 64 个字符") String name,
        @Min(value = 0, message = "父分类 ID 不能为负数") Long parentId,
        @Min(value = 0, message = "排序值不能为负数")
        @Max(value = 999_999, message = "排序值不能超过 999999") Integer sortOrder) {
}
