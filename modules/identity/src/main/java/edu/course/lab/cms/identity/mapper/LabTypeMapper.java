package edu.course.lab.cms.identity.mapper;

import edu.course.lab.cms.identity.entity.LabTypeRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LabTypeMapper {
    @Select("SELECT id, code, name FROM lab_type ORDER BY id")
    List<LabTypeRecord> findAll();

    @Select("SELECT id, code, name FROM lab_type WHERE id = #{id}")
    LabTypeRecord findById(long id);
}
