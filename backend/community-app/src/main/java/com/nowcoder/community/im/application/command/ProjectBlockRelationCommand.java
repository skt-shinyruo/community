package com.nowcoder.community.im.application.command;

import java.util.UUID;

public record ProjectBlockRelationCommand(
        String sourceDomain,
        String sourceEventId,
        UUID blockerUserId,
        UUID blockedUserId,
        boolean active,
        long occurredAtEpochMillis,
        long version
) {
}
