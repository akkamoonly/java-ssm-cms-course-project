package edu.course.lab.cms.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record AssignRolesRequest(@NotNull List<@Positive Long> roleIds) {
}
