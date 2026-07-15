package com.nowcoder.community.notice.application.command;

import java.time.Instant;
import java.util.UUID;

public sealed interface ProjectNoticeCommand permits
        ProjectNoticeCommand.CommentCreated,
        ProjectNoticeCommand.ModerationApplied,
        ProjectNoticeCommand.LikeCreated,
        ProjectNoticeCommand.LikeRemoved,
        ProjectNoticeCommand.FollowCreated {

    String sourceEventId();

    long sourceVersion();

    String sourceEventType();

    record CommentCreated(
            String sourceEventId,
            long sourceVersion,
            String sourceEventType,
            UUID commentId,
            UUID postId,
            UUID userId,
            int entityType,
            UUID entityId,
            UUID targetUserId,
            String content,
            Instant createTime
    ) implements ProjectNoticeCommand {
    }

    record ModerationApplied(
            String sourceEventId,
            long sourceVersion,
            String sourceEventType,
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
    ) implements ProjectNoticeCommand {
    }

    record LikeCreated(
            String sourceEventId,
            long sourceVersion,
            String sourceEventType,
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID entityUserId,
            UUID postId,
            String relationKey
    ) implements ProjectNoticeCommand {
    }

    record LikeRemoved(
            String sourceEventId,
            long sourceVersion,
            String sourceEventType,
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID entityUserId,
            UUID postId,
            String relationKey
    ) implements ProjectNoticeCommand {
    }

    record FollowCreated(
            String sourceEventId,
            long sourceVersion,
            String sourceEventType,
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID entityUserId,
            Instant createTime
    ) implements ProjectNoticeCommand {
    }
}
