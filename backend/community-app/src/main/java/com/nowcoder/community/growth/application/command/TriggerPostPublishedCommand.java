package com.nowcoder.community.growth.application.command;

import java.time.Instant;
import java.util.UUID;

public record TriggerPostPublishedCommand(UUID postId, UUID userId, Instant createTime) {
}
