package com.nowcoder.community.social.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class BlockRequest {

    @NotNull
    private UUID userId;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
