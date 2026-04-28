package com.nowcoder.community.growth.application.command;

import java.time.Instant;
import java.util.UUID;

public record TriggerLikeCreatedCommand(String sourceEventId, UUID actorUserId, UUID entityUserId, Instant createTime) {
}
