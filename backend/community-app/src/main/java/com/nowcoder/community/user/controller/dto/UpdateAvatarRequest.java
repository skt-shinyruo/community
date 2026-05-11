package com.nowcoder.community.user.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class UpdateAvatarRequest {

    @NotNull
    private UUID objectId;

    public UUID getObjectId() {
        return objectId;
    }

    public void setObjectId(UUID objectId) {
        this.objectId = objectId;
    }
}
