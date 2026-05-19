package com.nowcoder.community.im.common.event;

import java.util.UUID;

public record UserBlockRelationChanged(
        String eventId,
        UUID blockerUserId,
        UUID blockedUserId,
        boolean active,
        long occurredAtEpochMillis,
        Long version
) {

    public UserBlockRelationChanged(
            String eventId,
            UUID blockerUserId,
            UUID blockedUserId,
            boolean active,
            long occurredAtEpochMillis
    ) {
        this(eventId, blockerUserId, blockedUserId, active, occurredAtEpochMillis, null);
    }
}
