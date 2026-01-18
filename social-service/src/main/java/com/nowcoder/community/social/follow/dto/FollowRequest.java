package com.nowcoder.community.social.follow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class FollowRequest {

    @NotNull
    @Min(1)
    private Integer entityType;

    @NotNull
    @Min(1)
    private Integer entityId;

    private Integer entityUserId;

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
}

