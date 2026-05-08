package com.nowcoder.community.oss.application.command;

import java.util.UUID;

public record CreateSignedUrlCommand(
        UUID objectId,
        UUID versionId,
        long ttlSeconds,
        String actorId
) {
}
