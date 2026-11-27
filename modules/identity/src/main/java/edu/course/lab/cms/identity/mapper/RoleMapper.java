package edu.course.lab.cms.identity.mapper;

import edu.course.lab.cms.identity.entity.RoleRecord;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper {
    @Select("SELECT id, code, name, description FROM sys_role ORDER BY id")
    List<RoleRecord> findAll();

    @Select("SELECT id, code, name, description FROM sys_role WHERE id = #{id}")
    RoleRecord findById(long id);

    @Select("SELECT id, code, name, description FROM sys_role WHERE code = #{code}")
    RoleRecord findByCode(String code);

    @Select("""
            SELECT r.id, r.code, r.name, r.description
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id
            """)
    List<RoleRecord> findByUserId(long userId);

    @Select("""
            <script>
            SELECT COUNT(*) FROM sys_role WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countByIds(@Param("ids") Collection<Long> ids);
}
