package org.example.comment.dto;

import javax.validation.constraints.NotBlank;

public class CreateCommentRequest {
    private Long parentId;

    @NotBlank
    private String content;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

