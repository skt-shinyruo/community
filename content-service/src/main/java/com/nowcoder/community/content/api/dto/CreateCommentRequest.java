package com.nowcoder.community.content.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

public class CreateCommentRequest {

    @NotBlank
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
    @Min(1)
    private Integer entityId;

    /**
     * 目标用户（可选）。当回复评论时可指定；不传则自动使用被回复评论的作者。
     */
    private Integer targetId;

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

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public Integer getTargetId() {
        return targetId;
    }

    public void setTargetId(Integer targetId) {
        this.targetId = targetId;
    }
}
