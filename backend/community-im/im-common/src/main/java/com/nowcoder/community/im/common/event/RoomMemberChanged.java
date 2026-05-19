package com.nowcoder.community.im.common.event;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

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
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
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

    public RoomMemberChanged(
            String eventId,
            UUID roomId,
            UUID userId,
            String action,
            long occurredAtEpochMillis
    ) {
        this(eventId, roomId, userId, action, occurredAtEpochMillis, null,
                ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
    }
}
