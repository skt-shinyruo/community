package com.nowcoder.community.im.common.event;

import java.util.UUID;

public record UserMessagingPolicyChanged(
        String eventId,
        UUID userId,
        boolean userExists,
        boolean suspended,
        boolean muted,
        boolean allowPrivateMessages,
        long occurredAtEpochMillis
) {
}
