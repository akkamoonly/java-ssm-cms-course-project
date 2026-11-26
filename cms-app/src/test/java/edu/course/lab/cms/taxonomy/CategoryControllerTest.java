package edu.course.lab.cms.taxonomy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.api.ApiExceptionHandler;
import edu.course.lab.cms.platform.session.CurrentUser;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import edu.course.lab.cms.taxonomy.model.CategoryNode;
import edu.course.lab.cms.taxonomy.service.CategoryAdminService;
import edu.course.lab.cms.taxonomy.service.CategorySnapshot;
import edu.course.lab.cms.taxonomy.service.CategoryView;
import edu.course.lab.cms.taxonomy.web.AdminCategoryController;
import edu.course.lab.cms.taxonomy.web.CategoryController;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void anonymousUserReadsEnabledTreeOnly() throws Exception {
        var service = new StubService();
        try (var validator = validator()) {
            var response = mvc(new StaticListableBeanFactory(), validator, service)
                    .perform(get("/api/categories/tree"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse();
            var body = json.readTree(response.getContentAsByteArray());
            assertEquals("OK", body.path("code").asText());
            assertEquals(2L, body.path("data").get(0).path("id").asLong());
            assertEquals(1, body.path("data").get(0).path("children").size());
            assertEquals(false, service.lastIncludeDisabled);
        }
    }

    @Test
    void disabledNodesRequireCategoryPermission() throws Exception {
        try (var validator = validator()) {
            var anonymous = mvc(new StaticListableBeanFactory(), validator, new StubService())
                    .perform(get("/api/categories/tree").param("includeDisabled", "true"))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse();
            assertEquals("UNAUTHENTICATED", json.readTree(anonymous.getContentAsByteArray()).path("code").asText());

            var reader = mvc(usersWith(Set.of("article.create")), validator, new StubService())
                    .perform(get("/api/categories/tree").param("includeDisabled", "true"))
                    .andExpect(status().isForbidden()).andReturn().getResponse();
            assertEquals("FORBIDDEN", json.readTree(reader.getContentAsByteArray()).path("code").asText());

            var managerService = new StubService();
            mvc(usersWith(Set.of("category.manage")), validator, managerService)
                    .perform(get("/api/categories/tree").param("includeDisabled", "true"))
                    .andExpect(status().isOk());
            assertEquals(true, managerService.lastIncludeDisabled);
        }
    }

    @Test
    void adminEndpointsRejectMissingSessionAndMissingPermission() throws Exception {
        try (var validator = validator()) {
            var anonymous = mvc(new StaticListableBeanFactory(), validator, new StubService())
                    .perform(get("/api/admin/categories"))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse();
            assertEquals("UNAUTHENTICATED", json.readTree(anonymous.getContentAsByteArray()).path("code").asText());

            var reader = mvc(usersWith(Set.of()), validator, new StubService())
                    .perform(get("/api/admin/categories"))
                    .andExpect(status().isForbidden()).andReturn().getResponse();
            assertEquals("FORBIDDEN", json.readTree(reader.getContentAsByteArray()).path("code").asText());
        }
    }

    @Test
    void createValidatesNameAndDefaultsRootParent() throws Exception {
        var service = new StubService();
        try (var validator = validator()) {
            var mvc = mvc(usersWith(Set.of("category.manage")), validator, service);

            var blank = mvc.perform(post("/api/admin/categories")
                            .contentType("application/json")
                            .content("{\"name\":\"   \"}"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            var blankBody = json.readTree(blank.getContentAsByteArray());
            assertEquals("VALIDATION_FAILED", blankBody.path("code").asText());
            assertEquals("name", blankBody.path("data").get(0).path("field").asText());
            assertNull(service.createdArgs);

            var created = mvc.perform(post("/api/admin/categories")
                            .contentType("application/json")
                            .content("{\"name\":\" 校级制度 \"}"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            var body = json.readTree(created.getContentAsByteArray());
            assertEquals("OK", body.path("code").asText());
            assertEquals(0L, body.path("data").path("parentId").asLong());
            assertEquals(7L, service.createdArgs[0]);
            assertEquals(0L, service.createdArgs[1]);
            assertEquals(0L, service.createdArgs[2]);
            assertEquals(1L, service.createdArgs[3]);
        }
    }

    @Test
    void deleteConflictReturnsStableCode() throws Exception {
        var service = new StubService() {
            @Override
            public void delete(long actorId, long categoryId) {
                throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_HAS_CHILDREN", "请先删除或迁移子分类");
            }
        };
        try (var validator = validator()) {
            var response = mvc(usersWith(Set.of("category.manage")), validator, service)
                    .perform(delete("/api/admin/categories/1"))
                    .andExpect(status().isConflict()).andReturn().getResponse();
            var body = json.readTree(response.getContentAsByteArray());
            assertEquals("CATEGORY_HAS_CHILDREN", body.path("code").asText());
            assertEquals("请先删除或迁移子分类", body.path("message").asText());
        }
    }

    @Test
    void sortPathIsMatchedBeforeCategoryPut() throws Exception {
        var service = new StubService();
        try (var validator = validator()) {
            mvc(usersWith(Set.of("category.manage")), validator, service)
                    .perform(put("/api/admin/categories/sort")
                            .contentType("application/json")
                            .content("{\"parentId\":1,\"categoryIds\":[3,2]}"))
                    .andExpect(status().isOk());
            assertEquals(List.of(List.of(3L, 2L)), service.reordered);
            assertTrue(service.updatedIds.isEmpty());
        }
    }

    @Test
    void updateAndToggleUseActorFromSession() throws Exception {
        var service = new StubService();
        try (var validator = validator()) {
            var mvc = mvc(usersWith(Set.of("category.manage")), validator, service);
            mvc.perform(put("/api/admin/categories/3/enabled")
                            .contentType("application/json")
                            .content("{\"enabled\":false}"))
                    .andExpect(status().isOk());
            mvc.perform(put("/api/admin/categories/3")
                            .contentType("application/json")
                            .content("{\"name\":\"安全通报更新\",\"sortOrder\":15}"))
                    .andExpect(status().isOk());
            assertEquals(List.of(7L, 7L), service.actorIds);
            assertEquals(List.of(3L), service.updatedIds);
        }
    }

    @Test
    void sortRequestValidatesCategoryIds() throws Exception {
        try (var validator = validator()) {
            var empty = mvc(usersWith(Set.of("category.manage")), validator, new StubService())
                    .perform(put("/api/admin/categories/sort")
                            .contentType("application/json")
                            .content("{\"parentId\":1,\"categoryIds\":[]}"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(empty.getContentAsByteArray()).path("code").asText());

            var missing = mvc(usersWith(Set.of("category.manage")), validator, new StubService())
                    .perform(put("/api/admin/categories/sort")
                            .contentType("application/json")
                            .content("{\"parentId\":1}"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse();
            assertEquals("VALIDATION_FAILED", json.readTree(missing.getContentAsByteArray()).path("code").asText());
        }
    }

    /** 以桩服务验证 HTTP 契约，不触碰数据库。 */
    static class StubService implements CategoryAdminService {
        boolean lastIncludeDisabled = true;
        long[] createdArgs;
        final List<Long> actorIds = new ArrayList<>();
        final List<Long> updatedIds = new ArrayList<>();
        final List<List<Long>> reordered = new ArrayList<>();

        @Override
        public java.util.Optional<CategorySnapshot> findById(long categoryId) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean isPublishable(long categoryId) {
            return true;
        }

        @Override
        public List<Long> effectiveCategoryIds(long parentId) {
            return List.of();
        }

        @Override
        public List<CategoryNode> tree(boolean includeDisabled) {
            lastIncludeDisabled = includeDisabled;
            return List.of(new CategoryNode(2, 0, "培训指引", 20, true,
                    List.of(new CategoryNode(103, 2, "设备操作培训", 10, true, List.of()))));
        }

        @Override
        public List<CategoryView> list(boolean includeDisabled) {
            return List.of(view(2L, 0L, "培训指引", 20, true));
        }

        @Override
        public CategoryView create(long actorId, long parentId, String rawName, int sortOrder, boolean enabled) {
            createdArgs = new long[]{actorId, parentId, sortOrder, enabled ? 1 : 0};
            return view(9L, parentId, rawName.strip(), sortOrder, enabled);
        }

        @Override
        public CategoryView update(long actorId, long categoryId, Long parentId, String rawName, Integer sortOrder) {
            actorIds.add(actorId);
            updatedIds.add(categoryId);
            return view(categoryId, parentId == null ? 0L : parentId, rawName == null ? "旧名称" : rawName,
                    sortOrder == null ? 0 : sortOrder, true);
        }

        @Override
        public CategoryView setEnabled(long actorId, long categoryId, boolean enabled) {
            actorIds.add(actorId);
            return view(categoryId, 0L, "安全通报", 30, enabled);
        }

        @Override
        public List<CategoryView> reorder(long actorId, long parentId, List<Long> categoryIds) {
            reordered.add(List.copyOf(categoryIds));
            return List.of(view(2L, parentId, "实验室管理制度", 10, true));
        }

        @Override
        public void delete(long actorId, long categoryId) {
            updatedIds.add(categoryId);
        }
    }
    private static CategoryView view(long id, long parentId, String name, int sortOrder, boolean enabled) {
        return new CategoryView(id, parentId, name, sortOrder, enabled,
                LocalDateTime.of(2026, 11, 27, 8, 0), LocalDateTime.of(2026, 11, 27, 8, 0));
    }

    private static StaticListableBeanFactory usersWith(Set<String> permissions) {
        var users = new StaticListableBeanFactory();
        users.addBean("currentUserProvider", (CurrentUserProvider) () ->
                Optional.of(new CurrentUser(7L, 1L, permissions)));
        return users;
    }

    private static LocalValidatorFactoryBean validator() {
        var validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(new ParameterMessageInterpolator());
        validator.afterPropertiesSet();
        return validator;
    }

    private static MockMvc mvc(StaticListableBeanFactory users, LocalValidatorFactoryBean validator,
                               CategoryAdminService service) {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var provider = users.getBeanProvider(CurrentUserProvider.class);
        return MockMvcBuilders.standaloneSetup(
                        new CategoryController(service, provider),
                        new AdminCategoryController(service, provider))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setValidator(validator)
                .build();
    }
}
