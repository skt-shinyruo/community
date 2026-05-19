package com.nowcoder.community.im.common.projection;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

@ImJsonContract
public record RoomMembershipEntry(
        UUID roomId,
        UUID userId,
        Long version,
        Long occurredAtEpochMillis,
        @ImSchemaVersion
        int schemaVersion
) {

    public RoomMembershipEntry {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
    }

    public RoomMembershipEntry(UUID roomId, UUID userId, Long version, Long occurredAtEpochMillis) {
        this(roomId, userId, version, occurredAtEpochMillis, ImContractVersions.PROJECTION_SCHEMA_VERSION);
    }

    public RoomMembershipEntry(UUID roomId, UUID userId) {
        this(roomId, userId, null, null, ImContractVersions.PROJECTION_SCHEMA_VERSION);
    }
}
