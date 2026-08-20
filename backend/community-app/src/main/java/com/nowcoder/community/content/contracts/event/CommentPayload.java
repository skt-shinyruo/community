package com.nowcoder.community.content.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record CommentPayload(
        UUID commentId,
        UUID postId,
        UUID userId,
        int entityType,
        UUID entityId,
        UUID targetUserId,
        String content,
        Instant createTime,
        long postAggregateVersion
) {
}
