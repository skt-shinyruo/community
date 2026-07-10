package com.nowcoder.community.im.common.event;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;
import com.nowcoder.community.im.common.projection.ProjectionVersions;

import java.util.UUID;

@ImJsonContract
public record RoomMemberChanged(
        String eventId,
        UUID roomId,
        UUID userId,
        String action,
        long occurredAtEpochMillis,
        Long version,
        @ImSchemaVersion
        int schemaVersion
) {

    public RoomMemberChanged {
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
        version = ProjectionVersions.requirePositive(version, "version");
    }

    public RoomMemberChanged(
            String eventId,
            UUID roomId,
            UUID userId,
            String action,
            long occurredAtEpochMillis,
            Long version
    ) {
        this(eventId, roomId, userId, action, occurredAtEpochMillis, version,
                ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
    }

}
