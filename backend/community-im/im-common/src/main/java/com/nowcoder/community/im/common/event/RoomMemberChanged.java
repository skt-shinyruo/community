package com.nowcoder.community.im.common.event;

import java.util.UUID;

public record RoomMemberChanged(
        String eventId,
        UUID roomId,
        UUID userId,
        boolean active,
        long occurredAtEpochMillis
) {
}
