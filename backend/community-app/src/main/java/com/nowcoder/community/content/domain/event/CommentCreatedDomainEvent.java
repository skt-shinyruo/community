package com.nowcoder.community.content.domain.event;

import java.time.Instant;
import java.util.UUID;

public record CommentCreatedDomainEvent(
        UUID commentId,
        UUID postId,
        UUID userId,
        int entityType,
        UUID entityId,
        UUID targetUserId,
        String content,
        Instant createTime
) {
}
