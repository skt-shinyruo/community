package com.nowcoder.community.social.application.command;

import java.util.UUID;

public record SetLikeCommand(
        UUID actorUserId,
        int entityType,
        UUID entityId,
        Boolean liked,
        UUID entityUserId,
        UUID postId
) {
}
