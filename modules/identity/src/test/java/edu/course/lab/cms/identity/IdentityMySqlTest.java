package edu.course.lab.cms.identity;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.course.lab.cms.identity.mapper.RoleMapper;
import edu.course.lab.cms.identity.security.PermissionAspect;
import edu.course.lab.cms.identity.service.AuthService;
import edu.course.lab.cms.identity.service.LabTypeService;
import edu.course.lab.cms.identity.service.PermissionService;
import edu.course.lab.cms.identity.service.UserAdminService;
import edu.course.lab.cms.identity.session.SessionCurrentUserProvider;
import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.audit.AuditConstants;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 集成验证（需 CMS_VERIFY_MYSQL=true 及 CMS_DB_URL/CMS_DB_USER/CMS_DB_PASSWORD）。
 * 覆盖：种子账号 BCrypt 登录、注册默认读者角色与唯一约束、会话解析、
 * AOP 代理在真实 Spring 上下文中的 401/放行、角色分配审计与回滚。
 * 测试后恢复演示数据（user 4 的角色与类别），并清理本次创建的账号与审计行。
 */
@EnabledIfEnvironmentVariable(named = "CMS_VERIFY_MYSQL", matches = "true")
public class IdentityMySqlTest {
    @Test
    void identityModuleWorksAgainstRealMySql() {
        try (var context = new AnnotationConfigApplicationContext(IdentityDbConfig.class)) {
            var auth = context.getBean(AuthService.class);
            var permissions = context.getBean(PermissionService.class);
            var adminService = context.getBean(UserAdminService.class);
            var provider = context.getBean(CurrentUserProvider.class);
            var labTypes = context.getBean(LabTypeService.class);
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            var roles = context.getBean(RoleMapper.class);

            var before = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM cms_operation_log WHERE action = ? AND target_id = 4",
                    Long.class, AuditConstants.ACTION_ASSIGN_ROLE);

            try {
                // 1. 种子账号 BCrypt 登录；错误密码与用户不存在统一 401。
                var author = auth.login("author", "Author@123");
                assertEquals("AUTHOR", author.roles().get(0).code());
                assertEquals(List.of("article:create", "article:publish", "article:submit"),
                        List.copyOf(author.permissions()));
                assertEquals(401, assertThrows(ApiException.class, () -> auth.login("author", "wrong"))
                        .status().value());
                assertEquals(401, assertThrows(ApiException.class, () -> auth.login("nobody", "x12345"))
                        .status().value());

                // 2. 注册默认读者角色，用户名唯一。
                var registered = auth.register("itest_user", "itest-Password1", "集成测试");
                assertEquals("READER", registered.roles().get(0).code());
                var conflict = assertThrows(ApiException.class, () -> auth.register("itest_user", "whatever1", null));
                assertEquals(HttpStatus.CONFLICT, conflict.status());
                long registeredId = registered.id();
                assertTrue(permissions.resolveCurrentUser(registeredId).isPresent());

                // 3. 管理员权限解析（含 D 模块的 audit.read），实验室类别有效性。
                var admin = permissions.resolveCurrentUser(1L).orElseThrow();
                assertTrue(admin.permissions().contains("user:manage"));
                assertTrue(admin.permissions().contains("audit.read"));
                assertTrue(labTypes.isValid(1L));
                assertTrue(labTypes.isValid(null));
                assertTrue(!labTypes.isValid(999L));

                // 4. 无会话调用受保护方法：AOP 代理生效返回 401。
                RequestContextHolder.resetRequestAttributes();
                var unauthenticated = assertThrows(ApiException.class, () -> adminService.assignRoles(4L, List.of(3L)));
                assertEquals(401, unauthenticated.status().value());

                // 5. 管理员会话：角色分配成功并随业务事务写审计，权限立即生效。
                bindSession(provider, 1L);
                var assigned = adminService.assignRoles(4L, List.of(3L));
                assertEquals("REVIEWER", assigned.roles().get(0).code());
                assertTrue(permissions.resolveCurrentUser(4L).orElseThrow()
                        .permissions().contains("article:review"));
                var afterAssign = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM cms_operation_log WHERE action = ? AND target_id = 4",
                        Long.class, AuditConstants.ACTION_ASSIGN_ROLE);
                assertEquals(before + 1, afterAssign);

                // 6. 越权读者会话（此时 user 4 是审核员，无 user:manage）：返回 403。
                bindSession(provider, 4L);
                var forbidden = assertThrows(ApiException.class, () -> adminService.assignRoles(4L, List.of(1L)));
                assertEquals(403, forbidden.status().value());

                // 7. 无效角色：业务异常触发整体回滚，审计不落库，原角色不变。
                bindSession(provider, 1L);
                assertThrows(ApiException.class, () -> adminService.assignRoles(4L, List.of(999L)));
                var afterRollback = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM cms_operation_log WHERE action = ? AND target_id = 4",
                        Long.class, AuditConstants.ACTION_ASSIGN_ROLE);
                assertEquals(afterAssign, afterRollback);
                assertEquals("REVIEWER", roles.findByUserId(4L).get(0).code());

                // 8. 会话实时性：管理员改类别后，用户 4 的当前用户立即带上新类别。
                bindSession(provider, 1L);
                adminService.assignLabType(4L, 2L);
                bindSession(provider, 4L);
                var current = provider.currentUser().orElseThrow();
                assertEquals(2L, current.labTypeId());
            } finally {
                RequestContextHolder.resetRequestAttributes();
                jdbc.update("DELETE ur FROM sys_user_role ur JOIN sys_user u ON u.id = ur.user_id WHERE u.username = 'itest_user'");
                jdbc.update("DELETE FROM sys_user WHERE username = 'itest_user'");
                jdbc.update("DELETE FROM sys_user_role WHERE user_id = 4");
                jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (4, 1)");
                jdbc.update("UPDATE sys_user SET lab_type_id = 1 WHERE id = 4");
                jdbc.update("DELETE FROM cms_operation_log WHERE action = ? AND target_id = 4 AND actor_id = 1",
                        AuditConstants.ACTION_ASSIGN_ROLE);
            }
        }
    }

    private static void bindSession(CurrentUserProvider provider, long userId) {
        var request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(SessionCurrentUserProvider.SESSION_USER_ID, userId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Configuration
    @EnableTransactionManagement
    @EnableAspectJAutoProxy
    @ComponentScan(basePackages = {"edu.course.lab.cms.identity", "edu.course.lab.cms.platform.audit"},
            excludeFilters = {
                    @ComponentScan.Filter(type = FilterType.ANNOTATION,
                            classes = {Controller.class, ControllerAdvice.class}),
                    // 避免把同包测试里的 @Configuration 一并扫描进来
                    @ComponentScan.Filter(type = FilterType.ANNOTATION,
                            classes = org.springframework.context.annotation.Configuration.class)})
    @MapperScan(basePackages = {"edu.course.lab.cms.identity.mapper", "edu.course.lab.cms.platform.audit"},
            annotationClass = Mapper.class)
    public static class IdentityDbConfig {
        @Bean(destroyMethod = "close")
        HikariDataSource dataSource(Environment environment) {
            var config = new HikariConfig();
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl(environment.getRequiredProperty("CMS_DB_URL"));
            config.setUsername(environment.getRequiredProperty("CMS_DB_USER"));
            config.setPassword(environment.getRequiredProperty("CMS_DB_PASSWORD"));
            config.setPoolName("cms-identity-test");
            config.setMaximumPoolSize(2);
            config.addDataSourceProperty("connectionTimeZone", "UTC");
            config.setConnectionInitSql("SET time_zone = '+00:00'");
            return new HikariDataSource(config);
        }

        @Bean
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder() {
            return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        }

        @Bean
        org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
            pagination.setMaxLimit(100L);
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(pagination);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setPlugins(interceptor);
            return factory.getObject();
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
