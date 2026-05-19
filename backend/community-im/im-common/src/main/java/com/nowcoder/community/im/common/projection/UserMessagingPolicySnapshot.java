package com.nowcoder.community.im.common.projection;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.List;
import java.util.UUID;

@ImJsonContract
public record UserMessagingPolicySnapshot(
        List<UserMessagingPolicyEntry> entries,
        UUID nextUserId,
        boolean hasMore,
        Long snapshotHighWatermark,
        @ImSchemaVersion
        int schemaVersion
) {

    public UserMessagingPolicySnapshot {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
    }

    public UserMessagingPolicySnapshot(
            List<UserMessagingPolicyEntry> entries,
            UUID nextUserId,
            boolean hasMore,
            Long snapshotHighWatermark
    ) {
        this(entries, nextUserId, hasMore, snapshotHighWatermark,
                ImContractVersions.PROJECTION_SCHEMA_VERSION);
    }

    public UserMessagingPolicySnapshot(
            List<UserMessagingPolicyEntry> entries,
            UUID nextUserId,
            boolean hasMore
    ) {
        this(entries, nextUserId, hasMore, null, ImContractVersions.PROJECTION_SCHEMA_VERSION);
    }
}
