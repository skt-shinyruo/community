package com.nowcoder.community.im.common.projection;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

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
        Long occurredAtEpochMillis
) {

    public UserMessagingPolicyEntry(
            UUID userId,
            boolean userExists,
            boolean suspended,
            boolean muted,
            Long muteUntil,
            Long banUntil,
            boolean canSendPrivate
    ) {
        this(userId, userExists, suspended, muted, muteUntil, banUntil, canSendPrivate, null, null);
    }
}
