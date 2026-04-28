package com.nowcoder.community.content.application.result;

import java.util.Date;
import java.util.UUID;

public record RecentUserCommentResult(
        UUID id,
        UUID userId,
        int entityType,
        UUID entityId,
        UUID targetId,
        UUID postId,
        String postTitle,
        String content,
        Date createTime
) {
}
