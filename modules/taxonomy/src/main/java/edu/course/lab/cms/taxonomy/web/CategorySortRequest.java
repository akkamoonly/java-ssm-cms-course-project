package edu.course.lab.cms.taxonomy.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 同一父级下的排序请求，categoryIds 按目标展示顺序排列。 */
public record CategorySortRequest(
        @Min(value = 0, message = "父分类 ID 不能为负数") Long parentId,
        @NotEmpty(message = "排序列表不能为空")
        @Size(max = 200, message = "单次排序不能超过 200 个分类")
        List<@NotNull(message = "分类 ID 不能为空") Long> categoryIds) {

    public CategorySortRequest {
        parentId = parentId == null ? 0L : parentId;
    }
}
