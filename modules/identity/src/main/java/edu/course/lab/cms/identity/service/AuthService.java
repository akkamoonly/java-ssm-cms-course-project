package edu.course.lab.cms.identity.service;

import edu.course.lab.cms.identity.dto.UserView;
import edu.course.lab.cms.identity.entity.RoleRecord;
import edu.course.lab.cms.identity.entity.UserRecord;
import edu.course.lab.cms.identity.mapper.RoleMapper;
import edu.course.lab.cms.identity.mapper.UserMapper;
import edu.course.lab.cms.identity.mapper.UserRoleMapper;
import edu.course.lab.cms.platform.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 注册、登录与当前用户查询。注册默认绑定读者角色，实验室类别由管理员后续维护。 */
@Service
public class AuthService {
    private static final String READER_ROLE_CODE = "READER";

    private final UserMapper users;
    private final RoleMapper roles;
    private final UserRoleMapper userRoles;
    private final PermissionService permissionService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserMapper users, RoleMapper roles, UserRoleMapper userRoles,
            PermissionService permissionService, BCryptPasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserView register(String username, String password, String nickname) {
        if (users.findActiveByUsername(username) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "用户名已被注册");
        }
        RoleRecord reader = roles.findByCode(READER_ROLE_CODE);
        if (reader == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ROLE_NOT_INITIALIZED", "读者角色未初始化");
        }
        String displayName = nickname == null || nickname.isBlank() ? username : nickname.trim();
        var user = new UserRecord(null, username, passwordEncoder.encode(password), displayName, null, true, null);
        try {
            users.insert(user);
        } catch (DuplicateKeyException exception) {
            // 并发注册同一用户名的兜底：唯一索引拦截
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "用户名已被注册");
        }
        Long userId = users.findIdByUsername(username);
        userRoles.insert(userId, reader.id());
        return permissionService.describe(userId);
    }

    /** 校验凭据，成功返回用户视图；失败统一 401，不区分用户不存在或密码错误。 */
    @Transactional(readOnly = true)
    public UserView login(String username, String password) {
        UserRecord user = users.findActiveByUsername(username);
        if (user == null || !user.enabled() || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
        }
        return permissionService.describe(user.id());
    }

    /** 供 /api/auth/me 使用；会话有效但用户已被删除或停用时按未登录处理。 */
    @Transactional(readOnly = true)
    public UserView me(long userId) {
        UserView view = permissionService.describe(userId);
        if (view == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
        }
        return view;
    }
}
