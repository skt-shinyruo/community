package com.nowcoder.community.im.common.event;

import java.util.UUID;

public record UserBlockRelationChanged(
        String eventId,
        UUID blockerUserId,
        UUID blockedUserId,
        boolean active,
        long occurredAtEpochMillis
) {
}
