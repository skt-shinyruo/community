package com.nowcoder.community.user.contracts.event;

import java.util.UUID;

public record UserPolicyChangedPayload(
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
}
