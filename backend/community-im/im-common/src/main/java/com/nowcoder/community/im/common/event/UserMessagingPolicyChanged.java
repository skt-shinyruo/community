package com.nowcoder.community.im.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

@ImJsonContract
public record UserMessagingPolicyChanged(
        String eventId,
        UUID userId,
        boolean userExists,
        boolean suspended,
        boolean muted,
        Long muteUntil,
        Long banUntil,
        @JsonProperty("canSendPrivate")
        boolean canSendPrivate,
        long occurredAtEpochMillis,
        Long version,
        @ImSchemaVersion
        int schemaVersion
) {

    public UserMessagingPolicyChanged {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
    }

    public UserMessagingPolicyChanged(
            String eventId,
            UUID userId,
            boolean userExists,
            boolean suspended,
            boolean muted,
            Long muteUntil,
            Long banUntil,
            boolean canSendPrivate,
            long occurredAtEpochMillis,
            Long version
    ) {
        this(eventId, userId, userExists, suspended, muted, muteUntil, banUntil, canSendPrivate, occurredAtEpochMillis, version,
                ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
    }

    public UserMessagingPolicyChanged(
            String eventId,
            UUID userId,
            boolean userExists,
            boolean suspended,
            boolean muted,
            Long muteUntil,
            Long banUntil,
            boolean canSendPrivate,
            long occurredAtEpochMillis
    ) {
        this(eventId, userId, userExists, suspended, muted, muteUntil, banUntil, canSendPrivate, occurredAtEpochMillis, null,
                ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
    }
}
