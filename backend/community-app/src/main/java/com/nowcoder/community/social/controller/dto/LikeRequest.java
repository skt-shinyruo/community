package com.nowcoder.community.social.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class LikeRequest {

    @NotNull
    private Integer entityType;

    @NotNull
    private UUID entityId;

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

    public Boolean getLiked() {
        return liked;
    }

    public void setLiked(Boolean liked) {
        this.liked = liked;
    }
}
