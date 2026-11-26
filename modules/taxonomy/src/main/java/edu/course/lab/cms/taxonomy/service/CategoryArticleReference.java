package edu.course.lab.cms.taxonomy.service;

/**
 * 文章引用检查的扩展点。模块 C 建立 cms_article 后可以用一个 Bean 实现它，
 * 模块 B 在删除分类前就会先做引用检查；未实现时依赖 cms_article.category_id 的外键兜底。
 */
public interface CategoryArticleReference {
    long countByCategoryId(long categoryId);
}
