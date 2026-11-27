package edu.course.lab.cms.identity.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明受保护 Spring Bean 方法所需的权限标识。由 {@link PermissionAspect} 校验：
 * 未登录返回 401（UNAUTHENTICATED），缺少权限返回 403（FORBIDDEN）。
 * 注解应加在 Service 等 Spring Bean 的公开方法上，同类内部自调用不经过代理，不会生效。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String value();
}
