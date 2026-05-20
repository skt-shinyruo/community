package com.nowcoder.community.user.domain.model;

import java.time.Instant;
import java.util.UUID;

public record UserModerationStatus(
        UUID userId,
        Instant muteUntil,
        Instant banUntil,
        long version
) {

    public UserModerationStatus(UUID userId, Instant muteUntil, Instant banUntil) {
        this(userId, muteUntil, banUntil, 0L);
    }
}
