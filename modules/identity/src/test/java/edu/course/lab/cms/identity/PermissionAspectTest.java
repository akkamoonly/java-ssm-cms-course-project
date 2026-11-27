package edu.course.lab.cms.identity;

import edu.course.lab.cms.identity.security.PermissionAspect;
import edu.course.lab.cms.identity.security.RequiresPermission;
import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.session.CurrentUser;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证 @RequiresPermission 经 Spring AOP 代理生效：未登录 401、缺权限 403、有权限放行。 */
class PermissionAspectTest {
    private static final AtomicBoolean LOGGED_IN = new AtomicBoolean(false);
    private static final AtomicBoolean ADMIN = new AtomicBoolean(false);

    @Test
    void proxyEnforcesPermissionAnnotation() {
        try (var context = new AnnotationConfigApplicationContext(AspectConfig.class)) {
            var sample = context.getBean(SampleService.class);

            LOGGED_IN.set(false);
            ADMIN.set(false);
            var unauthenticated = assertThrows(ApiException.class, sample::manageCategory);
            assertEquals(401, unauthenticated.status().value());
            assertEquals("UNAUTHENTICATED", unauthenticated.code());

            LOGGED_IN.set(true);
            ADMIN.set(false);
            var forbidden = assertThrows(ApiException.class, sample::manageCategory);
            assertEquals(403, forbidden.status().value());
            assertEquals("FORBIDDEN", forbidden.code());

            ADMIN.set(true);
            assertEquals("managed", sample.manageCategory());
            assertEquals("always", sample.unprotected());
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class AspectConfig {
        @Bean
        CurrentUserProvider currentUserProvider() {
            return () -> LOGGED_IN.get()
                    ? Optional.of(new CurrentUser(1L, 1L,
                            ADMIN.get() ? Set.of("category:manage") : Set.of("article:create")))
                    : Optional.empty();
        }

        @Bean
        PermissionAspect permissionAspect(org.springframework.beans.factory.ObjectProvider<CurrentUserProvider> provider) {
            return new PermissionAspect(provider);
        }

        @Bean
        SampleService sampleService() {
            return new SampleService();
        }
    }

    public static class SampleService {
        @RequiresPermission("category:manage")
        public String manageCategory() {
            return "managed";
        }

        public String unprotected() {
            return "always";
        }
    }
}
