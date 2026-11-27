package edu.course.lab.cms.identity.service;

import edu.course.lab.cms.identity.entity.LabTypeRecord;
import edu.course.lab.cms.identity.mapper.LabTypeMapper;
import edu.course.lab.cms.platform.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 实验室类别查询。C 模块发布定向通报时用 isValid 校验目标类别。 */
@Service
public class LabTypeService {
    private final LabTypeMapper labTypes;

    public LabTypeService(LabTypeMapper labTypes) {
        this.labTypes = labTypes;
    }

    public List<LabTypeRecord> list() {
        return labTypes.findAll();
    }

    /** null 视为未分配（合法），其余必须是存在的类别 ID。 */
    public boolean isValid(Long labTypeId) {
        return labTypeId == null || labTypes.findById(labTypeId) != null;
    }

    public void requireValid(Long labTypeId) {
        if (!isValid(labTypeId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LAB_TYPE_NOT_FOUND", "实验室类别不存在");
        }
    }
}
