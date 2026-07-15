package com.nowcoder.community.interaction.application.command;

import java.util.UUID;

public record SetLikeInteractionCommand(
        UUID actorUserId,
        int entityType,
        UUID entityId,
        Boolean liked
) {
}
