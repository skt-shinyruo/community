package com.nowcoder.community.social.application.command;

import java.util.UUID;

public record FollowCommand(UUID actorUserId, int entityType, UUID entityId) {
}
