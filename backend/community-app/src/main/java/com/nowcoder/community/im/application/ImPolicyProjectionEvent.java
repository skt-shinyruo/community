package com.nowcoder.community.im.application;

import java.util.UUID;

public record ImPolicyProjectionEvent(
        String sourceDomain,
        String sourceEventId,
        String kind,
        UUID primaryUserId,
        UUID secondaryUserId,
        Boolean active,
        boolean userExists,
        boolean suspended,
        boolean muted,
        Long muteUntil,
        Long banUntil,
        boolean canSendPrivate,
        long occurredAtEpochMillis,
        long version
) {
}
