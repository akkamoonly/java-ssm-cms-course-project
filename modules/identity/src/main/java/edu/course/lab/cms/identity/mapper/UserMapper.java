package edu.course.lab.cms.identity.mapper;

import edu.course.lab.cms.identity.entity.UserRecord;
import edu.course.lab.cms.identity.entity.UserSummary;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {
    @Insert("""
            INSERT INTO sys_user (username, password_hash, nickname, lab_type_id)
            VALUES (#{username}, #{passwordHash}, #{nickname}, #{labTypeId})
            """)
    int insert(UserRecord user);

    @Select("""
            SELECT id FROM sys_user
            WHERE username = #{username} AND deleted = 0
            """)
    Long findIdByUsername(String username);

    @Select("""
            SELECT id, username, password_hash, nickname, lab_type_id, enabled, created_at
            FROM sys_user
            WHERE username = #{username} AND deleted = 0
            """)
    UserRecord findActiveByUsername(String username);

    @Select("""
            SELECT id, username, password_hash, nickname, lab_type_id, enabled, created_at
            FROM sys_user
            WHERE id = #{id} AND deleted = 0
            """)
    UserRecord findActiveById(long id);

    @Select("""
            <script>
            SELECT id, username, nickname, lab_type_id, enabled, created_at
            FROM sys_user
            WHERE deleted = 0
            <if test="keyword != null and keyword != ''">
                AND (username LIKE CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY id ASC
            </script>
            """)
    List<UserSummary> search(com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserSummary> page,
            @Param("keyword") String keyword);

    @Update("UPDATE sys_user SET lab_type_id = #{labTypeId} WHERE id = #{userId} AND deleted = 0")
    int updateLabType(@Param("userId") long userId, @Param("labTypeId") Long labTypeId);
}
