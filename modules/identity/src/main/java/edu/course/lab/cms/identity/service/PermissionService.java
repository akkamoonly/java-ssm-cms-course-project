package edu.course.lab.cms.identity.service;

import edu.course.lab.cms.identity.dto.RoleView;
import edu.course.lab.cms.identity.dto.UserView;
import edu.course.lab.cms.identity.entity.LabTypeRecord;
import edu.course.lab.cms.identity.entity.UserRecord;
import edu.course.lab.cms.identity.mapper.LabTypeMapper;
import edu.course.lab.cms.identity.mapper.PermissionMapper;
import edu.course.lab.cms.identity.mapper.RoleMapper;
import edu.course.lab.cms.identity.mapper.UserMapper;
import edu.course.lab.cms.platform.session.CurrentUser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 汇总用户的角色、权限与实验室类别，向会话解析和其他模块提供统一的当前用户信息。 */
@Service
public class PermissionService {
    private final UserMapper users;
    private final RoleMapper roles;
    private final PermissionMapper permissions;
    private final LabTypeMapper labTypes;

    public PermissionService(UserMapper users, RoleMapper roles, PermissionMapper permissions,
            LabTypeMapper labTypes) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.labTypes = labTypes;
    }

    /** 按用户 ID 解析当前用户：用户不存在、已删除或已停用时返回空（对应 401）。 */
    public Optional<CurrentUser> resolveCurrentUser(long userId) {
        var user = users.findActiveById(userId);
        if (user == null || !user.enabled()) {
            return Optional.empty();
        }
        return Optional.of(new CurrentUser(user.id(), user.labTypeId(),
                Set.copyOf(permissions.findCodesByUserId(user.id()))));
    }

    /** 组装对外用户信息（角色按 id 排序，权限按编码排序去重）。 */
    public UserView describe(long userId) {
        var user = users.findActiveById(userId);
        if (user == null) {
            return null;
        }
        return describe(user);
    }

    public UserView describe(UserRecord user) {
        List<RoleView> roleViews = roles.findByUserId(user.id()).stream()
                .map(role -> new RoleView(role.id(), role.code(), role.name()))
                .toList();
        Set<String> codes = new LinkedHashSet<>(permissions.findCodesByUserId(user.id()));
        String labTypeName = null;
        if (user.labTypeId() != null) {
            LabTypeRecord labType = labTypes.findById(user.labTypeId());
            labTypeName = labType == null ? null : labType.name();
        }
        return new UserView(user.id(), user.username(), user.nickname(), user.labTypeId(), labTypeName,
                user.enabled(), roleViews, codes, user.createdAt());
    }
}
