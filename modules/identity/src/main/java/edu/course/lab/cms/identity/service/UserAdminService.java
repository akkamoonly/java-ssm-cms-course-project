package edu.course.lab.cms.identity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.course.lab.cms.identity.IdentityPermissions;
import edu.course.lab.cms.identity.dto.UserView;
import edu.course.lab.cms.identity.entity.UserRecord;
import edu.course.lab.cms.identity.entity.UserSummary;
import edu.course.lab.cms.identity.mapper.RoleMapper;
import edu.course.lab.cms.identity.mapper.UserMapper;
import edu.course.lab.cms.identity.mapper.UserRoleMapper;
import edu.course.lab.cms.identity.security.RequiresPermission;
import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.api.PageResult;
import edu.course.lab.cms.platform.audit.AuditConstants;
import edu.course.lab.cms.platform.audit.AuditService;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理端账号维护：用户分页查询、角色分配与实验室类别分配。权限校验在方法注解上声明。 */
@Service
public class UserAdminService {
    private final UserMapper users;
    private final RoleMapper roles;
    private final UserRoleMapper userRoles;
    private final LabTypeService labTypes;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final CurrentUserProvider currentUserProvider;

    public UserAdminService(UserMapper users, RoleMapper roles, UserRoleMapper userRoles,
            LabTypeService labTypes, PermissionService permissionService, AuditService audit,
            CurrentUserProvider currentUserProvider) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.labTypes = labTypes;
        this.permissionService = permissionService;
        this.audit = audit;
        this.currentUserProvider = currentUserProvider;
    }

    @RequiresPermission(IdentityPermissions.USER_MANAGE)
    @Transactional(readOnly = true)
    public PageResult<UserView> list(String keyword, long page, long size) {
        var mpPage = new Page<UserSummary>(page, size);
        List<UserSummary> rows = users.search(mpPage, keyword == null || keyword.isBlank() ? null : keyword.trim());
        List<UserView> items = rows.stream().map(row -> permissionService.describe(row.id())).toList();
        return new PageResult<>(items, mpPage.getCurrent(), mpPage.getSize(), mpPage.getTotal());
    }

    /** 覆盖式角色分配：整体替换用户的角色集合，每个被授予的角色写一条 ASSIGN_ROLE 成功审计。 */
    @RequiresPermission(IdentityPermissions.USER_MANAGE)
    @Transactional
    public UserView assignRoles(long userId, List<Long> roleIds) {
        requireUser(userId);
        if (roleIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "角色 ID 必须为正数");
        }
        Set<Long> distinct = new LinkedHashSet<>(roleIds);
        if (distinct.size() < roleIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "角色 ID 重复");
        }
        if (!distinct.isEmpty() && roles.countByIds(distinct) != distinct.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "存在无效的角色 ID");
        }
        userRoles.deleteByUserId(userId);
        long actorId = currentUserProvider.currentUser()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录"))
                .userId();
        for (long roleId : distinct) {
            userRoles.insert(userId, roleId);
            audit.recordSuccess(actorId, AuditConstants.ACTION_ASSIGN_ROLE, AuditConstants.TARGET_USER, userId);
        }
        return permissionService.describe(userId);
    }

    /** 分配或清除实验室类别（labTypeId 为 null 表示清除）。 */
    @RequiresPermission(IdentityPermissions.USER_MANAGE)
    @Transactional
    public UserView assignLabType(long userId, Long labTypeId) {
        requireUser(userId);
        labTypes.requireValid(labTypeId);
        if (users.updateLabType(userId, labTypeId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return permissionService.describe(userId);
    }

    private UserRecord requireUser(long userId) {
        var user = users.findActiveById(userId);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }
}
