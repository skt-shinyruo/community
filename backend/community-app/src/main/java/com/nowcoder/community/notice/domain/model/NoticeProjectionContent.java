package com.nowcoder.community.notice.domain.model;

import java.time.Instant;
import java.util.UUID;

public sealed interface NoticeProjectionContent permits
        NoticeProjectionContent.Comment,
        NoticeProjectionContent.Moderation,
        NoticeProjectionContent.Like,
        NoticeProjectionContent.Follow {

    record Comment(
            UUID commentId,
            UUID postId,
            UUID userId,
            int entityType,
            UUID entityId,
            UUID targetUserId,
            String content,
            Instant createTime
    ) implements NoticeProjectionContent {
    }

    record Moderation(
            UUID reportId,
            String kind,
            UUID toUserId,
            UUID actorUserId,
            Integer targetType,
            UUID targetId,
            String action,
            String reason,
            Integer durationSeconds,
            Instant createTime
    ) implements NoticeProjectionContent {
    }

    record Like(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID entityUserId,
            UUID postId,
            String relationKey
    ) implements NoticeProjectionContent {
    }

    record Follow(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID entityUserId,
            Instant createTime
    ) implements NoticeProjectionContent {
    }
}
