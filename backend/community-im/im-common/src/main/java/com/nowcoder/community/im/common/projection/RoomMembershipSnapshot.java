package com.nowcoder.community.im.common.projection;

import java.util.List;
import java.util.UUID;

public record RoomMembershipSnapshot(
        List<RoomMembershipEntry> entries,
        UUID nextRoomId,
        UUID nextUserId,
        boolean hasMore,
        Long snapshotHighWatermark
) {

    public RoomMembershipSnapshot(
            List<RoomMembershipEntry> entries,
            UUID nextRoomId,
            UUID nextUserId,
            boolean hasMore
    ) {
        this(entries, nextRoomId, nextUserId, hasMore, null);
    }
}
