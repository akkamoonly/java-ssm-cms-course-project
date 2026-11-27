package edu.course.lab.cms.identity;

/** 权限标识常量，与 B、C、D 在共享接口草案中对齐（小写点分格式）。 */
public final class IdentityPermissions {
    public static final String USER_MANAGE = "user:manage";
    public static final String CATEGORY_MANAGE = "category:manage";
    public static final String ARTICLE_CREATE = "article:create";
    public static final String ARTICLE_SUBMIT = "article:submit";
    public static final String ARTICLE_REVIEW = "article:review";
    public static final String ARTICLE_PUBLISH = "article:publish";
    public static final String AUDIT_READ = "audit.read";

    private IdentityPermissions() {
    }
}
