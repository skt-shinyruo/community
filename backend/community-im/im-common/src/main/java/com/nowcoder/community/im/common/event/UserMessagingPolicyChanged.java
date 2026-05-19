package com.nowcoder.community.im.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

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
        Long version
) {

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
        this(eventId, userId, userExists, suspended, muted, muteUntil, banUntil, canSendPrivate, occurredAtEpochMillis, null);
    }
}
