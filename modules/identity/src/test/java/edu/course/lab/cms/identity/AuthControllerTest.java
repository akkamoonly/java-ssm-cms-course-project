package edu.course.lab.cms.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.course.lab.cms.identity.controller.AuthController;
import edu.course.lab.cms.identity.dto.RoleView;
import edu.course.lab.cms.identity.dto.UserView;
import edu.course.lab.cms.identity.service.AuthService;
import edu.course.lab.cms.identity.session.SessionCurrentUserProvider;
import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.api.ApiExceptionHandler;
import edu.course.lab.cms.platform.session.CurrentUser;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 认证接口 MockMvc 测试：注册校验、登录建立会话、退出失效、me 的 401/200。 */
class AuthControllerTest {
    private final ObjectMapper json = new ObjectMapper();

    AuthControllerTest() {
        json.registerModule(new JavaTimeModule());
        json.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static final UserView AUTHOR_VIEW = new UserView(2L, "author", "演示作者", 1L, "机电类",
            true, List.of(new RoleView(2L, "AUTHOR", "作者")),
            Set.of("article:create", "article:submit", "article:publish"),
            LocalDateTime.of(2026, 11, 27, 0, 0, 0));

    @Test
    void registerValidatesInputAndCreatesReader() throws Exception {
        var auth = stubAuthService();
        try (var validator = validator()) {
            var mvc = mvc(auth, () -> Optional.empty(), validator);

            var invalidUsername = mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"坏名字\",\"password\":\"secret1\"}"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(invalidUsername.getContentAsByteArray()).path("code").asText());

            var shortPassword = mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"newuser\",\"password\":\"123\"}"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(shortPassword.getContentAsByteArray()).path("code").asText());

            var unreadable = mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("not-json"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("INVALID_REQUEST_BODY", json.readTree(unreadable.getContentAsByteArray()).path("code").asText());

            var ok = mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"newuser\",\"password\":\"secret1\",\"nickname\":\"新用户\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            var body = json.readTree(ok.getContentAsByteArray());
            assertEquals("OK", body.path("code").asText());
            assertEquals(7L, body.path("data").path("id").asLong());
            assertEquals("READER", body.path("data").path("roles").get(0).path("code").asText());
        }
    }

    @Test
    void loginEstablishesSessionAndRejectsBadCredentials() throws Exception {
        var auth = stubAuthService();
        try (var validator = validator()) {
            var mvc = mvc(auth, () -> Optional.empty(), validator);

            var badLogin = mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"author\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse();
            assertEquals("INVALID_CREDENTIALS", json.readTree(badLogin.getContentAsByteArray()).path("code").asText());

            var ok = mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"author\",\"password\":\"Author@123\"}"))
                    .andExpect(status().isOk()).andReturn();
            var body = json.readTree(ok.getResponse().getContentAsByteArray());
            assertEquals("OK", body.path("code").asText());
            assertEquals(2L, body.path("data").path("id").asLong());
            var session = ok.getRequest().getSession(false);
            assertNotNull(session);
            assertEquals(2L, session.getAttribute(SessionCurrentUserProvider.SESSION_USER_ID));
        }
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        var auth = stubAuthService();
        try (var validator = validator()) {
            var mvc = mvc(auth, () -> Optional.empty(), validator);
            var result = mvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk()).andReturn();
            assertEquals("OK", json.readTree(result.getResponse().getContentAsByteArray()).path("code").asText());
            assertNull(result.getRequest().getSession(false));
        }
    }

    @Test
    void meReturnsCurrentUserOrUnauthorized() throws Exception {
        try (var validator = validator()) {
            var anonymous = mvc(stubAuthService(), Optional::empty, validator).perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse();
            assertEquals("UNAUTHENTICATED", json.readTree(anonymous.getContentAsByteArray()).path("code").asText());

            var loggedIn = mvc(stubAuthService(), () -> Optional.of(new CurrentUser(2L, 1L, Set.of())), validator)
                    .perform(get("/api/auth/me"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            var body = json.readTree(loggedIn.getContentAsByteArray());
            assertEquals("OK", body.path("code").asText());
            assertEquals("author", body.path("data").path("username").asText());
            assertEquals("机电类", body.path("data").path("labTypeName").asText());
        }
    }

    private static AuthService stubAuthService() {
        return new AuthService(null, null, null, null, null) {
            @Override
            public UserView register(String username, String password, String nickname) {
                if (nickname != null && nickname.equals("新用户")) {
                    return new UserView(7L, "newuser", "新用户", null, null, true,
                            List.of(new RoleView(1L, "READER", "读者")), Set.of(),
                            LocalDateTime.of(2026, 11, 27, 1, 0, 0));
                }
                throw new IllegalStateException("unexpected register call");
            }

            @Override
            public UserView login(String username, String password) {
                if ("author".equals(username) && "Author@123".equals(password)) {
                    return AUTHOR_VIEW;
                }
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
            }

            @Override
            public UserView me(long userId) {
                return AUTHOR_VIEW;
            }
        };
    }

    private static LocalValidatorFactoryBean validator() {
        var validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(new ParameterMessageInterpolator());
        validator.afterPropertiesSet();
        return validator;
    }

    private static MockMvc mvc(AuthService auth, CurrentUserProvider provider, LocalValidatorFactoryBean validator) {
        return MockMvcBuilders.standaloneSetup(new AuthController(auth, provider))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper().registerModule(new JavaTimeModule())
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .setValidator(validator)
                .build();
    }
}
