package com.nowcoder.community.im.common.projection;

import java.util.List;
import java.util.UUID;

public record UserMessagingPolicySnapshot(
        List<UserMessagingPolicyEntry> entries,
        UUID nextUserId,
        boolean hasMore,
        Long snapshotHighWatermark
) {

    public UserMessagingPolicySnapshot(
            List<UserMessagingPolicyEntry> entries,
            UUID nextUserId,
            boolean hasMore
    ) {
        this(entries, nextUserId, hasMore, null);
    }
}
