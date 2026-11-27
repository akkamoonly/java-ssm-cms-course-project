package edu.course.lab.cms.identity.entity;

/** sys_user 列表查询投影，不含密码哈希。 */
public record UserSummary(long id, String username, String nickname,
        Long labTypeId, boolean enabled, java.time.LocalDateTime createdAt) {
}
