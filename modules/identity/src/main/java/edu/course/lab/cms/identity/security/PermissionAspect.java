package edu.course.lab.cms.identity.security;

import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 在受保护方法调用前校验会话与权限标识。 */
@Aspect
@Component
public class PermissionAspect {
    private final ObjectProvider<CurrentUserProvider> userProvider;

    public PermissionAspect(ObjectProvider<CurrentUserProvider> userProvider) {
        this.userProvider = userProvider;
    }

    // argNames 显式声明参数名：未使用 -parameters 编译时反射拿不到真实参数名，注解绑定会失败。
    @Around(value = "@annotation(requiresPermission)", argNames = "joinPoint,requiresPermission")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission)
            throws Throwable {
        var current = Optional.ofNullable(userProvider.getIfAvailable())
                .flatMap(CurrentUserProvider::currentUser)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录"));
        if (!current.permissions().contains(requiresPermission.value())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有操作权限: " + requiresPermission.value());
        }
        return joinPoint.proceed();
    }
}
