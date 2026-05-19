package com.nowcoder.community.im.common.projection;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

@ImJsonContract
public record UserMessagingPolicyEntry(
        UUID userId,
        boolean userExists,
        boolean suspended,
        boolean muted,
        Long muteUntil,
        Long banUntil,
        @JsonProperty("canSendPrivate")
        boolean canSendPrivate,
        Long version,
        Long occurredAtEpochMillis,
        @ImSchemaVersion
        int schemaVersion
) {

    public UserMessagingPolicyEntry {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
    }

    public UserMessagingPolicyEntry(
            UUID userId,
            boolean userExists,
            boolean suspended,
            boolean muted,
            Long muteUntil,
            Long banUntil,
            boolean canSendPrivate,
            Long version,
            Long occurredAtEpochMillis
    ) {
        this(userId, userExists, suspended, muted, muteUntil, banUntil, canSendPrivate, version, occurredAtEpochMillis,
                ImContractVersions.PROJECTION_SCHEMA_VERSION);
    }

    public UserMessagingPolicyEntry(
            UUID userId,
            boolean userExists,
            boolean suspended,
            boolean muted,
            Long muteUntil,
            Long banUntil,
            boolean canSendPrivate
    ) {
        this(userId, userExists, suspended, muted, muteUntil, banUntil, canSendPrivate, null, null,
                ImContractVersions.PROJECTION_SCHEMA_VERSION);
    }
}
