package com.nowcoder.community.growth.application.command;

import java.time.Instant;
import java.util.UUID;

public record TriggerCommentCreatedCommand(UUID commentId, UUID userId, Instant createTime) {
}
