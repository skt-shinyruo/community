package com.nowcoder.community.social.application.command;

import java.util.UUID;

public record UnfollowCommand(UUID actorUserId, int entityType, UUID entityId) {
}
