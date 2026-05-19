package com.nowcoder.community.im.common.projection;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.List;
import java.util.UUID;

@ImJsonContract
public record UserBlockRelationSnapshot(
        List<UserBlockRelationEntry> entries,
        UUID nextBlockerUserId,
        UUID nextBlockedUserId,
        boolean hasMore,
        Long snapshotHighWatermark,
        @ImSchemaVersion
        int schemaVersion
) {

    public UserBlockRelationSnapshot {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
    }

    public UserBlockRelationSnapshot(
            List<UserBlockRelationEntry> entries,
            UUID nextBlockerUserId,
            UUID nextBlockedUserId,
            boolean hasMore,
            Long snapshotHighWatermark
    ) {
        this(entries, nextBlockerUserId, nextBlockedUserId, hasMore, snapshotHighWatermark,
                ImContractVersions.PROJECTION_SCHEMA_VERSION);
    }

    public UserBlockRelationSnapshot(
            List<UserBlockRelationEntry> entries,
            UUID nextBlockerUserId,
            UUID nextBlockedUserId,
            boolean hasMore
    ) {
        this(entries, nextBlockerUserId, nextBlockedUserId, hasMore, null,
                ImContractVersions.PROJECTION_SCHEMA_VERSION);
    }
}
