package com.nowcoder.community.im.common.projection;

import java.util.UUID;

public record RoomMembershipEntry(
        UUID roomId,
        UUID userId,
        Long version,
        Long occurredAtEpochMillis
) {

    public RoomMembershipEntry(UUID roomId, UUID userId) {
        this(roomId, userId, null, null);
    }
}
