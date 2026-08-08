package com.nowcoder.community.growth.application.command;

import java.util.UUID;

public record TriggerLikeRemovedCommand(
        String sourceEventId,
        long sourceVersion,
        String relationKey,
        UUID relationInstanceId,
        UUID entityUserId
) {
}
