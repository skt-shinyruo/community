package com.nowcoder.community.im.common.event;

import java.util.UUID;

public record RoomMemberChanged(
        String eventId,
        UUID roomId,
        UUID userId,
        String action,
        long occurredAtEpochMillis,
        Long version
) {

    public RoomMemberChanged(
            String eventId,
            UUID roomId,
            UUID userId,
            String action,
            long occurredAtEpochMillis
    ) {
        this(eventId, roomId, userId, action, occurredAtEpochMillis, null);
    }
}
