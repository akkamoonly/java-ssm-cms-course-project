package edu.course.lab.cms.identity.controller;

import edu.course.lab.cms.identity.dto.AdminUserQuery;
import edu.course.lab.cms.identity.dto.AssignLabTypeRequest;
import edu.course.lab.cms.identity.dto.AssignRolesRequest;
import edu.course.lab.cms.identity.dto.UserView;
import edu.course.lab.cms.identity.entity.LabTypeRecord;
import edu.course.lab.cms.identity.service.LabTypeService;
import edu.course.lab.cms.identity.service.UserAdminService;
import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.api.ApiResponse;
import edu.course.lab.cms.platform.api.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端账号接口。写操作权限（user:manage）在 Service 方法上由 AOP 统一校验。 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {
    private final UserAdminService userAdmin;
    private final LabTypeService labTypes;

    public AdminUserController(UserAdminService userAdmin, LabTypeService labTypes) {
        this.userAdmin = userAdmin;
        this.labTypes = labTypes;
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<UserView>> listUsers(@Valid @ModelAttribute AdminUserQuery query) {
        return ApiResponse.success(userAdmin.list(query.getKeyword(), query.getPage(), query.getSize()));
    }

    @PutMapping("/users/{userId}/roles")
    public ApiResponse<UserView> assignRoles(@PathVariable("userId") long userId,
            @Valid @RequestBody AssignRolesRequest body) {
        requirePositiveUserId(userId);
        return ApiResponse.success(userAdmin.assignRoles(userId, body.roleIds()));
    }

    @PutMapping("/users/{userId}/lab-type")
    public ApiResponse<UserView> assignLabType(@PathVariable("userId") long userId,
            @RequestBody AssignLabTypeRequest body) {
        requirePositiveUserId(userId);
        return ApiResponse.success(userAdmin.assignLabType(userId, body.labTypeId()));
    }

    @GetMapping("/lab-types")
    public ApiResponse<List<LabTypeRecord>> listLabTypes() {
        return ApiResponse.success(labTypes.list());
    }

    private static void requirePositiveUserId(long userId) {
        if (userId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "用户 ID 必须为正数");
        }
    }
}
