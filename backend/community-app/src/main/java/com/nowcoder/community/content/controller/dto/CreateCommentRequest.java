package com.nowcoder.community.content.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.common.constants.ValidationLimits;

import java.util.UUID;

public class CreateCommentRequest {

    @NotBlank
    @Size(max = ValidationLimits.COMMENT_CONTENT_MAX)
    private String content;

    private UUID parentCommentId;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public UUID getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(UUID parentCommentId) {
        this.parentCommentId = parentCommentId;
    }
}
