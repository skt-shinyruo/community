package com.nowcoder.community.content.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.common.constants.ValidationLimits;

import java.util.UUID;

public class CreateCommentRequest {

    @NotBlank
    @Size(max = ValidationLimits.COMMENT_CONTENT_MAX)
    private String content;

    /**
     * 兼容旧单体：entityType=1 表示评论帖子；entityType=2 表示回复评论。
     * 默认 1。
     */
    @Min(1)
    private Integer entityType;

    /**
     * 当 entityType=2 时，entityId 为被回复的 commentId。
     */
    private UUID entityId;

    /**
     * 目标用户（可选）。当回复评论时可指定；不传则自动使用被回复评论的作者。
     */
    private UUID targetId;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getEntityType() {
        return entityType;
    }

    public void setEntityType(Integer entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }
}
