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
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
        version = ProjectionVersions.requirePositive(version, "version");
    }

    public RoomMembershipEntry(UUID roomId, UUID userId, Long version, Long occurredAtEpochMillis) {
        this(roomId, userId, version, occurredAtEpochMillis, ImContractVersions.PROJECTION_SCHEMA_VERSION);
    }

}
