package com.nowcoder.community.growth.application.command;

import java.time.Instant;
import java.util.UUID;

public record TriggerLikeCreatedCommand(
        String sourceEventId,
        long sourceVersion,
        String relationKey,
        UUID relationInstanceId,
        UUID actorUserId,
        UUID entityUserId,
        Instant createTime
) {
}
