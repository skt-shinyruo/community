package com.nowcoder.community.social.like.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class LikeRequest {

    @NotNull
    private Integer entityType;

    @NotNull
    private UUID entityId;

    private UUID entityUserId;

    private UUID postId;

    private Boolean liked;

    public int getEntityType() {
        return entityType == null ? 0 : entityType;
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

    public UUID getEntityUserId() {
        return entityUserId;
    }

    public void setEntityUserId(UUID entityUserId) {
        this.entityUserId = entityUserId;
    }

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }

    public Boolean getLiked() {
        return liked;
    }

    public void setLiked(Boolean liked) {
        this.liked = liked;
    }
}
