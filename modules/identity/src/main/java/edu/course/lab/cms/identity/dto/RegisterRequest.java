package edu.course.lab.cms.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只允许字母、数字和下划线")
        String username,

        @NotBlank @Size(min = 6, max = 64)
        String password,

        @Size(max = 64)
        String nickname) {
}
