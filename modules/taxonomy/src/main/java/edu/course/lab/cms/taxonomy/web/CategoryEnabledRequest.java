package edu.course.lab.cms.taxonomy.web;

import jakarta.validation.constraints.NotNull;

public record CategoryEnabledRequest(@NotNull(message = "启停状态不能为空") Boolean enabled) {
}
