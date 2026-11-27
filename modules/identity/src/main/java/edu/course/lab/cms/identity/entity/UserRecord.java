package edu.course.lab.cms.identity.entity;

import java.time.LocalDateTime;

/** sys_user 行记录。deleted 列由查询显式过滤，不载入内存。 */
public record UserRecord(Long id, String username, String passwordHash, String nickname,
        Long labTypeId, boolean enabled, LocalDateTime createdAt) {
}
