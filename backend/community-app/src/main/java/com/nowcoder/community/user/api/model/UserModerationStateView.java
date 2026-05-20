package com.nowcoder.community.user.api.model;

import java.time.Instant;
import java.util.UUID;

public record UserModerationStateView(UUID userId, Instant muteUntil, Instant banUntil, long version) {

    public UserModerationStateView(UUID userId, Instant muteUntil, Instant banUntil) {
        this(userId, muteUntil, banUntil, 0L);
    }
}
