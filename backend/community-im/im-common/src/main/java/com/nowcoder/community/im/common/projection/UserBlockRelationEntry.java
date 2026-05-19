package com.nowcoder.community.im.common.projection;

import java.util.UUID;

public record UserBlockRelationEntry(
        UUID blockerUserId,
        UUID blockedUserId,
        boolean active,
        Long version,
        Long occurredAtEpochMillis
) {

    public UserBlockRelationEntry(
            UUID blockerUserId,
            UUID blockedUserId,
            boolean active
    ) {
        this(blockerUserId, blockedUserId, active, null, null);
    }
}
