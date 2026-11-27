package edu.course.lab.cms.identity.dto;

/** labTypeId 为 null 表示清除用户的实验室类别。 */
public record AssignLabTypeRequest(Long labTypeId) {
}
