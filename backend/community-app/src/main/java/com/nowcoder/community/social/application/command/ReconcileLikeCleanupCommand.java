package com.nowcoder.community.social.application.command;

import java.util.UUID;

public record ReconcileLikeCleanupCommand(
        int entityType,
        UUID afterEntityId,
        int batchSize
) {
}
