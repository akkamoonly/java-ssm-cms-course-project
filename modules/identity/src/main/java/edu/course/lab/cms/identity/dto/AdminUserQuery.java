package edu.course.lab.cms.identity.dto;

import edu.course.lab.cms.platform.api.PageQuery;
import jakarta.validation.constraints.Size;

public class AdminUserQuery extends PageQuery {
    @Size(max = 64)
    private String keyword;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
