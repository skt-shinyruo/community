package com.nowcoder.community.im.common.projection;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.List;
import java.util.UUID;

@ImJsonContract
public record RoomMembershipSnapshot(
        List<RoomMembershipEntry> entries,
        UUID nextRoomId,
        UUID nextUserId,
        boolean hasMore,
        Long snapshotHighWatermark,
        @ImSchemaVersion
        int schemaVersion
) {

    public RoomMembershipSnapshot {
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
        snapshotHighWatermark = ProjectionVersions.requireNonNegative(
                snapshotHighWatermark,
                "snapshotHighWatermark"
        );
    }

    public RoomMembershipSnapshot(
            List<RoomMembershipEntry> entries,
            UUID nextRoomId,
            UUID nextUserId,
            boolean hasMore,
            Long snapshotHighWatermark
    ) {
        this(entries, nextRoomId, nextUserId, hasMore, snapshotHighWatermark,
                ImContractVersions.PROJECTION_SCHEMA_VERSION);
    }

}
