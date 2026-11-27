package edu.course.lab.cms.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.course.lab.cms.identity.controller.AdminUserController;
import edu.course.lab.cms.identity.dto.RoleView;
import edu.course.lab.cms.identity.dto.UserView;
import edu.course.lab.cms.identity.entity.LabTypeRecord;
import edu.course.lab.cms.identity.service.LabTypeService;
import edu.course.lab.cms.identity.service.UserAdminService;
import edu.course.lab.cms.platform.api.ApiExceptionHandler;
import edu.course.lab.cms.platform.api.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 管理端账号接口 MockMvc 测试：参数校验、分页与请求体约束。权限 401/403 由 AOP 层测试覆盖。 */
class AdminUserControllerTest {
    private final ObjectMapper json = new ObjectMapper();

    AdminUserControllerTest() {
        json.registerModule(new JavaTimeModule());
        json.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void listUsersPaginatesWithKeyword() throws Exception {
        try (var validator = validator()) {
            var mvc = mvc(validator);
            var ok = mvc.perform(get("/api/admin/users")
                            .param("keyword", "reader")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            var body = json.readTree(ok.getContentAsByteArray());
            assertEquals("OK", body.path("code").asText());
            assertEquals(2L, body.path("data").path("total").asLong());
            assertEquals("reader_mech", body.path("data").path("items").get(0).path("username").asText());

            var badSize = mvc.perform(get("/api/admin/users").param("size", "101"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(badSize.getContentAsByteArray()).path("code").asText());
        }
    }

    @Test
    void assignRolesValidatesPathAndBody() throws Exception {
        try (var validator = validator()) {
            var mvc = mvc(validator);

            var nullRoles = mvc.perform(put("/api/admin/users/4/roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roleIds\": null}"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(nullRoles.getContentAsByteArray()).path("code").asText());

            var invalidId = mvc.perform(put("/api/admin/users/4/roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roleIds\": [0]}"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(invalidId.getContentAsByteArray()).path("code").asText());

            var badPath = mvc.perform(put("/api/admin/users/0/roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roleIds\": [3]}"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(badPath.getContentAsByteArray()).path("code").asText());

            var ok = mvc.perform(put("/api/admin/users/4/roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roleIds\": [3]}"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            var body = json.readTree(ok.getContentAsByteArray());
            assertEquals("OK", body.path("code").asText());
            assertEquals("REVIEWER", body.path("data").path("roles").get(0).path("code").asText());
        }
    }

    @Test
    void assignLabTypeAndListLabTypes() throws Exception {
        try (var validator = validator()) {
            var mvc = mvc(validator);

            var ok = mvc.perform(put("/api/admin/users/4/lab-type")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"labTypeId\": 2}"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            assertEquals(2L, json.readTree(ok.getContentAsByteArray()).path("data").path("labTypeId").asLong());

            var types = mvc.perform(get("/api/admin/lab-types"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            var body = json.readTree(types.getContentAsByteArray());
            assertEquals("ELECTROMECHANICAL", body.path("data").get(0).path("code").asText());
            assertEquals("机电类", body.path("data").get(0).path("name").asText());
        }
    }

    private static MockMvc mvc(LocalValidatorFactoryBean validator) {
        var admin = new UserAdminService(null, null, null, null, null, null, null) {
            @Override
            public PageResult<UserView> list(String keyword, long page, long size) {
                var mech = new UserView(4L, "reader_mech", "机电类读者", 1L, "机电类", true,
                        List.of(new RoleView(1L, "READER", "读者")), Set.of(),
                        LocalDateTime.of(2026, 11, 27, 0, 0, 0));
                var other = new UserView(5L, "reader_other", "其他类读者", 2L, "其他类", true,
                        List.of(new RoleView(1L, "READER", "读者")), Set.of(),
                        LocalDateTime.of(2026, 11, 27, 0, 0, 0));
                return new PageResult<>(List.of(mech, other), page, size, 2L);
            }

            @Override
            public UserView assignRoles(long userId, List<Long> roleIds) {
                return new UserView(userId, "reader_mech", "机电类读者", 1L, "机电类", true,
                        List.of(new RoleView(3L, "REVIEWER", "审核员")), Set.of("article:review"),
                        LocalDateTime.of(2026, 11, 27, 0, 0, 0));
            }

            @Override
            public UserView assignLabType(long userId, Long labTypeId) {
                return new UserView(userId, "reader_mech", "机电类读者", labTypeId, "其他类", true,
                        List.of(new RoleView(1L, "READER", "读者")), Set.of(),
                        LocalDateTime.of(2026, 11, 27, 0, 0, 0));
            }
        };
        var labTypes = new LabTypeService(null) {
            @Override
            public List<LabTypeRecord> list() {
                return List.of(new LabTypeRecord(1L, "ELECTROMECHANICAL", "机电类"),
                        new LabTypeRecord(2L, "OTHER", "其他类"));
            }
        };
        return MockMvcBuilders.standaloneSetup(new AdminUserController(admin, labTypes))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper().registerModule(new JavaTimeModule())
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .setValidator(validator)
                .build();
    }

    private static LocalValidatorFactoryBean validator() {
        var validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(new ParameterMessageInterpolator());
        validator.afterPropertiesSet();
        return validator;
    }
}
