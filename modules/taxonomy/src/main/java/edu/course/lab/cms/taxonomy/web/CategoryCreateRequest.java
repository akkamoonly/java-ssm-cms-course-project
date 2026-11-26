package edu.course.lab.cms.taxonomy.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增分类请求，parentId 为空表示挂在根下。 */
public record CategoryCreateRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 64, message = "分类名称不能超过 64 个字符") String name,
        @Min(value = 0, message = "父分类 ID 不能为负数") Long parentId,
        @Min(value = 0, message = "排序值不能为负数")
        @Max(value = 999_999, message = "排序值不能超过 999999") Integer sortOrder,
        Boolean enabled) {
}
