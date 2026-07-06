package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.ContentEventPublisher;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "content.events.publisher", havingValue = "local")
public class LocalContentEventPublisher implements ContentEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public LocalContentEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishPostPublished(PostPayload payload) {
        UUID postId = payload == null ? null : payload.getPostId();
        if (postId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.POST_PUBLISHED, payload.getCreateTime());
        publish(
                UUID.randomUUID().toString(),
                ContentEventTypes.POST_PUBLISHED,
                postId,
                "post",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        );
    }

    @Override
    public void publishPostUpdated(PostPayload payload) {
        UUID postId = payload == null ? null : payload.getPostId();
        if (postId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(
                ContentEventTypes.POST_UPDATED,
                payload.getUpdateTime() == null ? payload.getCreateTime() : payload.getUpdateTime()
        );
        publish(
                UUID.randomUUID().toString(),
                ContentEventTypes.POST_UPDATED,
                postId,
                "post",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        );
    }

    @Override
    public void publishPostDeleted(PostPayload payload) {
        UUID postId = payload == null ? null : payload.getPostId();
        if (postId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(
                ContentEventTypes.POST_DELETED,
                payload.getUpdateTime() == null ? payload.getCreateTime() : payload.getUpdateTime()
        );
        publish(
                UUID.randomUUID().toString(),
                ContentEventTypes.POST_DELETED,
                postId,
                "post",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        );
    }

    @Override
    public void publishCommentCreated(CommentPayload payload) {
        UUID commentId = payload == null ? null : payload.getCommentId();
        if (commentId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.COMMENT_CREATED, payload.getCreateTime());
        publish(
                UUID.randomUUID().toString(),
                ContentEventTypes.COMMENT_CREATED,
                commentId,
                "comment",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        );
    }

    @Override
    public void publishCommentDeleted(CommentPayload payload) {
        UUID commentId = payload == null ? null : payload.getCommentId();
        if (commentId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.COMMENT_DELETED, payload.getCreateTime());
        publish(
                UUID.randomUUID().toString(),
                ContentEventTypes.COMMENT_DELETED,
                commentId,
                "comment",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        );
    }

    @Override
    public void publishModerationActionApplied(ModerationPayload payload) {
        UUID toUserId = payload == null ? null : payload.getToUserId();
        if (toUserId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.MODERATION_ACTION_APPLIED, payload.getCreateTime());
        publish(
                UUID.randomUUID().toString(),
                ContentEventTypes.MODERATION_ACTION_APPLIED,
                toUserId,
                "user",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        );
    }

    private void publish(
            String eventId,
            String type,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            Object payload
    ) {
        applicationEventPublisher.publishEvent(new ContentContractEvent(
                eventId,
                aggregateId,
                aggregateType,
                type,
                occurredAt,
                version,
                payload
        ));
    }

    private Instant requiredOccurredAt(String type, Instant occurredAt) {
        if (occurredAt == null) {
            throw new IllegalStateException("content event source occurredAt missing: " + type);
        }
        return occurredAt;
    }

    private long positiveVersion(Instant occurredAt) {
        return Math.max(1L, occurredAt.toEpochMilli());
    }
}
