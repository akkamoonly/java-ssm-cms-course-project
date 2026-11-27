package edu.course.lab.cms.identity.session;

import edu.course.lab.cms.identity.service.PermissionService;
import edu.course.lab.cms.platform.session.CurrentUser;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从服务端会话解析当前用户：会话只保存 userId，角色、权限与实验室类别每次请求实时查询，
 * 管理员调整角色或类别后立即生效，无需重新登录。匿名请求或会话失效返回空。
 */
@Component
public class SessionCurrentUserProvider implements CurrentUserProvider {
    public static final String SESSION_USER_ID = "CMS_AUTH_USER_ID";

    private final PermissionService permissionService;

    public SessionCurrentUserProvider(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public Optional<CurrentUser> currentUser() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return Optional.empty();
        }
        var session = servlet.getRequest().getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        var userId = session.getAttribute(SESSION_USER_ID);
        if (!(userId instanceof Long id)) {
            return Optional.empty();
        }
        return permissionService.resolveCurrentUser(id);
    }
}
