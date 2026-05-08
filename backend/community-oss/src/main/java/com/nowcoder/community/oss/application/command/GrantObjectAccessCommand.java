package com.nowcoder.community.oss.application.command;

import java.time.Instant;
import java.util.UUID;

public record GrantObjectAccessCommand(
        UUID objectId,
        UUID versionId,
        String principalType,
        String principalValue,
        String permission,
        Instant expiresAt,
        String actorId
) {
}
