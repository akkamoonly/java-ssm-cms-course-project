package edu.course.lab.cms.taxonomy.web;

import edu.course.lab.cms.platform.api.ApiException;
import edu.course.lab.cms.platform.session.CurrentUser;
import edu.course.lab.cms.platform.session.CurrentUserProvider;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

/**
 * 分类接口的会话与权限入口。身份模块尚未落地时统一按未登录处理，
 * 权限标识与共享接口草案一致，使用 category.manage。
 */
public final class CategoryAccess {
    public static final String MANAGE_PERMISSION = "category.manage";

    private CategoryAccess() {
    }

    public static CurrentUser requireManager(ObjectProvider<CurrentUserProvider> users) {
        var user = Optional.ofNullable(users.getIfAvailable())
                .flatMap(CurrentUserProvider::currentUser)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录"));
        if (!user.permissions().contains(MANAGE_PERMISSION)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有分类管理权限");
        }
        return user;
    }
}
