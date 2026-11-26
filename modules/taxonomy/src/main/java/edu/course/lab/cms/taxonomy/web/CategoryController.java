package edu.course.lab.cms.taxonomy.web;

import edu.course.lab.cms.platform.api.ApiResponse;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import edu.course.lab.cms.taxonomy.model.CategoryNode;
import edu.course.lab.cms.taxonomy.service.CategoryQueryService;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 读者端的分类树，默认只返回有效分类。 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryQueryService categories;
    private final ObjectProvider<CurrentUserProvider> users;

    public CategoryController(CategoryQueryService categories, ObjectProvider<CurrentUserProvider> users) {
        this.categories = categories;
        this.users = users;
    }

    @GetMapping("/tree")
    public ApiResponse<List<CategoryNode>> tree(
            @RequestParam(name = "includeDisabled", defaultValue = "false") boolean includeDisabled) {
        if (includeDisabled) {
            // 停用节点只对具备分类管理权限的管理员可见。
            CategoryAccess.requireManager(users);
        }
        return ApiResponse.success(categories.tree(includeDisabled));
    }
}
