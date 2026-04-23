package com.nowcoder.community.im.common.event;

import com.fasterxml.jackson.annotation.JsonAlias;
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
        @JsonAlias("allowPrivateMessages")
        boolean allowPrivateMessages,
        long occurredAtEpochMillis
) {
}
