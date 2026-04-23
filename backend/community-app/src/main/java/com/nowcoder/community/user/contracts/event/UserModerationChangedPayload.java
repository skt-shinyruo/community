package com.nowcoder.community.user.contracts.event;

import java.util.UUID;

public class UserModerationChangedPayload {

    private UUID userId;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
