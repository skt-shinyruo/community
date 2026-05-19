package com.nowcoder.community.im.common.event;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

@ImJsonContract
public record UserBlockRelationChanged(
        String eventId,
        UUID blockerUserId,
        UUID blockedUserId,
        boolean active,
        long occurredAtEpochMillis,
        Long version,
        @ImSchemaVersion
        int schemaVersion
) {

    public UserBlockRelationChanged {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
    }

    public UserBlockRelationChanged(
            String eventId,
            UUID blockerUserId,
            UUID blockedUserId,
            boolean active,
            long occurredAtEpochMillis,
            Long version
    ) {
        this(eventId, blockerUserId, blockedUserId, active, occurredAtEpochMillis, version,
                ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
    }

    public UserBlockRelationChanged(
            String eventId,
            UUID blockerUserId,
            UUID blockedUserId,
            boolean active,
            long occurredAtEpochMillis
    ) {
        this(eventId, blockerUserId, blockedUserId, active, occurredAtEpochMillis, null,
                ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
    }
}
