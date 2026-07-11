package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.content.application.ContentEventPublisher;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OutboxContentEventPublisher implements ContentEventPublisher {

    private final JsonCodec jsonCodec;
    private final JdbcOutboxEventStore store;
    private final String topic;
    private final UuidV7Generator idGenerator = new UuidV7Generator();

    public OutboxContentEventPublisher(
            JsonCodec jsonCodec,
            JdbcOutboxEventStore store,
            @Value("${content.events.outbox-topic:eventbus.content}") String topic
    ) {
        this.jsonCodec = jsonCodec;
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
        publish(
                "content:PostPublished:" + postId,
                ContentEventTypes.POST_PUBLISHED,
                postId.toString(),
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
                "ce:post:updated:" + idGenerator.next(),
                ContentEventTypes.POST_UPDATED,
                postId.toString(),
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
                "content:PostDeleted:" + postId,
                ContentEventTypes.POST_DELETED,
                postId.toString(),
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
                "content:CommentCreated:" + commentId,
                ContentEventTypes.COMMENT_CREATED,
                commentId.toString(),
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
                "content:CommentDeleted:" + commentId,
                ContentEventTypes.COMMENT_DELETED,
                commentId.toString(),
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
                "ce:moderation:" + idGenerator.next(),
                ContentEventTypes.MODERATION_ACTION_APPLIED,
                toUserId.toString(),
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
            String key,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            Object payload
    ) {
        String payloadJson;
        try {
            payloadJson = jsonCodec.toJson(new ContentContractEvent(
                    eventId,
                    aggregateId,
                    aggregateType,
                    type,
                    occurredAt,
                    version,
                    payload
            ));
        } catch (JsonCodecException e) {
            throw new IllegalStateException("content event outbox payload serialization failed: " + type, e);
        }
        store.enqueue(eventId, topic, key, payloadJson);
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
