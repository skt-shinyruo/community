package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.content.application.ContentEventPublisher;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ContentTypedEvent;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OutboxContentEventPublisher implements ContentEventPublisher {

    private final ContentContractEventCodec contractEventCodec;
    private final JdbcOutboxEventStore store;
    private final String topic;
    private final UuidV7Generator idGenerator = new UuidV7Generator();

    public OutboxContentEventPublisher(
            ContentContractEventCodec contractEventCodec,
            JdbcOutboxEventStore store,
            @Value("${content.events.outbox-topic:eventbus.content}") String topic
    ) {
        this.contractEventCodec = contractEventCodec;
        this.store = store;
        this.topic = topic;
    }

    @Override
    public void publishPostPublished(PostPayload payload) {
        UUID postId = payload == null ? null : payload.getPostId();
        if (postId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.POST_PUBLISHED, payload.getCreateTime());
        publish(new ContentTypedEvent.PostPublished(
                "content:PostPublished:" + postId,
                postId,
                "post",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        ), postId.toString());
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
        publish(new ContentTypedEvent.PostUpdated(
                "ce:post:updated:" + idGenerator.next(),
                postId,
                "post",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        ), postId.toString());
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
        publish(new ContentTypedEvent.PostDeleted(
                "content:PostDeleted:" + postId,
                postId,
                "post",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        ), postId.toString());
    }

    @Override
    public void publishCommentCreated(CommentPayload payload) {
        UUID commentId = payload == null ? null : payload.getCommentId();
        if (commentId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.COMMENT_CREATED, payload.getCreateTime());
        publish(new ContentTypedEvent.CommentCreated(
                "content:CommentCreated:" + commentId,
                commentId,
                "comment",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        ), commentId.toString());
    }

    @Override
    public void publishCommentDeleted(CommentPayload payload) {
        UUID commentId = payload == null ? null : payload.getCommentId();
        if (commentId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.COMMENT_DELETED, payload.getCreateTime());
        publish(new ContentTypedEvent.CommentDeleted(
                "content:CommentDeleted:" + commentId,
                commentId,
                "comment",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        ), commentId.toString());
    }

    @Override
    public void publishModerationActionApplied(ModerationPayload payload) {
        UUID toUserId = payload == null ? null : payload.getToUserId();
        if (toUserId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.MODERATION_ACTION_APPLIED, payload.getCreateTime());
        publish(new ContentTypedEvent.ModerationActionApplied(
                "ce:moderation:" + idGenerator.next(),
                toUserId,
                "user",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        ), toUserId.toString());
    }

    private void publish(ContentTypedEvent event, String key) {
        String payloadJson;
        try {
            payloadJson = contractEventCodec.serialize(event);
        } catch (JsonCodecException e) {
            throw new IllegalStateException(
                    "content event outbox payload serialization failed: " + event.getClass().getSimpleName(), e);
        }
        store.enqueue(event.eventId(), topic, key, payloadJson);
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
