package edu.course.lab.cms.identity.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 对外暴露的用户信息，不含密码哈希。 */
public record UserView(long id, String username, String nickname, Long labTypeId, String labTypeName,
        boolean enabled, List<RoleView> roles, Set<String> permissions, LocalDateTime createdAt) {
}
