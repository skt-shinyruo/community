package com.nowcoder.community.social.like.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class LikeRequest {

    @NotNull
    @Min(1)
    private Integer entityType;

    @NotNull
    @Min(1)
    private Integer entityId;

    private Integer entityUserId;

    private Integer postId;

    private Boolean liked;

    public int getEntityType() {
        return entityType == null ? 0 : entityType;
    }

    public void setEntityType(Integer entityType) {
        this.entityType = entityType;
    }

    public int getEntityId() {
        return entityId == null ? 0 : entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public Integer getEntityUserId() {
        return entityUserId;
    }

    public void setEntityUserId(Integer entityUserId) {
        this.entityUserId = entityUserId;
    }

    public Integer getPostId() {
        return postId;
    }

    public void setPostId(Integer postId) {
        this.postId = postId;
    }

    public Boolean getLiked() {
        return liked;
    }

    public void setLiked(Boolean liked) {
        this.liked = liked;
    }
}
