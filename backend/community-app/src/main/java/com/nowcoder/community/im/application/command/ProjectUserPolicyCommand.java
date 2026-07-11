package com.nowcoder.community.im.application.command;

import java.util.UUID;

public record ProjectUserPolicyCommand(
        String sourceDomain,
        String sourceEventId,
        UUID userId,
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
