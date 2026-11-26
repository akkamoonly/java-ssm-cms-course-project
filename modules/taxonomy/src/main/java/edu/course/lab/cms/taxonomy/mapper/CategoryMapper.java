package edu.course.lab.cms.taxonomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.course.lab.cms.taxonomy.model.Category;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    /** 一次批量取回整个分类表，树在内存里组装。 */
    @Select("""
            SELECT id, parent_id, name, sort_order, enabled, created_at, updated_at
            FROM cms_category
            ORDER BY parent_id, sort_order, id
            """)
    List<Category> findAll();

    @Select("SELECT COUNT(*) FROM cms_category WHERE parent_id = #{parentId}")
    long countChildren(@Param("parentId") long parentId);

    @Select("""
            SELECT COUNT(*) FROM cms_category
            WHERE parent_id = #{parentId} AND name = #{name} AND id <> #{excludeId}
            """)
    long countSiblingName(@Param("parentId") long parentId, @Param("name") String name,
                          @Param("excludeId") long excludeId);
}
