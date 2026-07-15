package com.nowcoder.community.social.application.command;

import java.time.Instant;
import java.util.UUID;

public record CleanupDeletedContentLikesCommand(
        int entityType,
        UUID entityId,
        String sourceEventId,
        long sourceVersion,
        Instant deletedAt
) {
}
