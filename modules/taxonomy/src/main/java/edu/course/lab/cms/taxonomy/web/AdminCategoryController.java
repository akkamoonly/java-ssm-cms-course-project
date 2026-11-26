package edu.course.lab.cms.taxonomy.web;

import edu.course.lab.cms.platform.api.ApiResponse;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import edu.course.lab.cms.taxonomy.service.CategoryAdminService;
import edu.course.lab.cms.taxonomy.service.CategoryView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 分类后台管理接口，全部要求 category.manage 权限。 */
@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {
    private final CategoryAdminService categories;
    private final ObjectProvider<CurrentUserProvider> users;

    public AdminCategoryController(CategoryAdminService categories, ObjectProvider<CurrentUserProvider> users) {
        this.categories = categories;
        this.users = users;
    }

    @GetMapping
    public ApiResponse<List<CategoryView>> list(
            @RequestParam(name = "includeDisabled", defaultValue = "true") boolean includeDisabled) {
        CategoryAccess.requireManager(users);
        return ApiResponse.success(categories.list(includeDisabled));
    }

    @PostMapping
    public ApiResponse<CategoryView> create(@Valid @RequestBody CategoryCreateRequest request) {
        var user = CategoryAccess.requireManager(users);
        long parentId = request.parentId() == null ? 0L : request.parentId();
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        boolean enabled = request.enabled() == null || request.enabled();
        return ApiResponse.success(categories.create(user.userId(), parentId, request.name(), sortOrder, enabled));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryView> update(@PathVariable(name = "id") long id,
                                            @Valid @RequestBody CategoryUpdateRequest request) {
        var user = CategoryAccess.requireManager(users);
        return ApiResponse.success(categories.update(user.userId(), id, request.parentId(), request.name(),
                request.sortOrder()));
    }

    @PutMapping("/{id}/enabled")
    public ApiResponse<CategoryView> setEnabled(@PathVariable(name = "id") long id,
                                                @Valid @RequestBody CategoryEnabledRequest request) {
        var user = CategoryAccess.requireManager(users);
        return ApiResponse.success(categories.setEnabled(user.userId(), id, request.enabled()));
    }

    /** 与 /{id} 同前缀，Spring 优先匹配字面量路径。 */
    @PutMapping("/sort")
    public ApiResponse<List<CategoryView>> sort(@Valid @RequestBody CategorySortRequest request) {
        var user = CategoryAccess.requireManager(users);
        return ApiResponse.success(categories.reorder(user.userId(), request.parentId(), request.categoryIds()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable(name = "id") long id) {
        var user = CategoryAccess.requireManager(users);
        categories.delete(user.userId(), id);
        return ApiResponse.success(null);
    }
}

