package com.nowcoder.community.im.common.projection;

import java.util.List;
import java.util.UUID;

public record UserBlockRelationSnapshot(
        List<UserBlockRelationEntry> entries,
        UUID nextBlockerUserId,
        UUID nextBlockedUserId,
        boolean hasMore,
        Long snapshotHighWatermark
) {

    public UserBlockRelationSnapshot(
            List<UserBlockRelationEntry> entries,
            UUID nextBlockerUserId,
            UUID nextBlockedUserId,
            boolean hasMore
    ) {
        this(entries, nextBlockerUserId, nextBlockedUserId, hasMore, null);
    }
}
