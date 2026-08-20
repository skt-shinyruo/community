package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.content.application.ContentEventPublisher;
import com.nowcoder.community.content.application.ModerationNoticePublisher;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ContentTypedEvent;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.contracts.event.PostScorePayload;
import com.nowcoder.community.content.domain.model.ModerationActionRecord;
import com.nowcoder.community.content.domain.model.ModerationTarget;
import com.nowcoder.community.content.domain.model.ReportSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class OutboxContentEventPublisher implements ContentEventPublisher, ModerationNoticePublisher {

    private final ContentContractEventCodec contractEventCodec;
    private final JdbcOutboxEventStore store;
    private final String topic;
    private final UuidV7Generator idGenerator;
    private final Clock clock;

    public OutboxContentEventPublisher(
            ContentContractEventCodec contractEventCodec,
            JdbcOutboxEventStore store,
            @Value("${content.events.outbox-topic:eventbus.content}") String topic,
            UuidV7Generator idGenerator,
            Clock clock
    ) {
        this.contractEventCodec = Objects.requireNonNull(contractEventCodec, "contractEventCodec must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.topic = Objects.requireNonNull(topic, "topic must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void publishPostPublished(PostPayload payload) {
        UUID postId = payload == null ? null : payload.postId();
        if (postId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.POST_PUBLISHED, payload.createTime());
        publish(new ContentTypedEvent.PostPublished(
                "content:PostPublished:" + postId,
                postId,
                "post",
                occurredAt,
                requiredPostVersion(payload),
                payload
        ), postId.toString());
    }

    @Override
    public void publishPostUpdated(PostPayload payload) {
        UUID postId = payload == null ? null : payload.postId();
        if (postId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(
                ContentEventTypes.POST_UPDATED,
                payload.updateTime() == null ? payload.createTime() : payload.updateTime()
        );
        publish(new ContentTypedEvent.PostUpdated(
                "ce:post:updated:" + idGenerator.next(),
                postId,
                "post",
                occurredAt,
                requiredPostVersion(payload),
                payload
        ), postId.toString());
    }

    @Override
    public void publishPostScoreUpdated(PostScorePayload payload) {
        if (payload == null) {
            return;
        }
        UUID postId = payload.postId();
        Instant occurredAt = clock.instant();
        publish(new ContentTypedEvent.PostScoreUpdated(
                "content:PostScoreUpdated:" + postId + ":" + payload.scoreVersion(),
                postId,
                "post",
                occurredAt,
                payload.scoreVersion(),
                payload
        ), postId.toString());
    }

    @Override
    public void publishPostDeleted(PostPayload payload) {
        UUID postId = payload == null ? null : payload.postId();
        if (postId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(
                ContentEventTypes.POST_DELETED,
                payload.updateTime() == null ? payload.createTime() : payload.updateTime()
        );
        publish(new ContentTypedEvent.PostDeleted(
                "content:PostDeleted:" + postId,
                postId,
                "post",
                occurredAt,
                requiredPostVersion(payload),
                payload
        ), postId.toString());
    }

    @Override
    public void publishCommentCreated(CommentPayload payload) {
        UUID commentId = payload == null ? null : payload.commentId();
        if (commentId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.COMMENT_CREATED, payload.createTime());
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
        UUID commentId = payload == null ? null : payload.commentId();
        if (commentId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.COMMENT_DELETED, payload.createTime());
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
        UUID toUserId = payload == null ? null : payload.toUserId();
        if (toUserId == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(ContentEventTypes.MODERATION_ACTION_APPLIED, payload.createTime());
        publish(new ContentTypedEvent.ModerationActionApplied(
                "ce:moderation:" + idGenerator.next(),
                toUserId,
                "user",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        ), toUserId.toString());
    }

    @Override
    public void publish(
            ReportSnapshot report,
            ModerationActionRecord action,
            ModerationTarget target,
            String kind,
            UUID toUserId
    ) {
        if (toUserId == null) {
            return;
        }
        ModerationPayload payload = new ModerationPayload(
                report == null ? null : report.id(),
                kind,
                toUserId,
                action == null ? null : action.actorId(),
                target == null ? null : target.targetType(),
                target == null ? null : target.targetId(),
                action == null ? null : action.action(),
                action == null ? null : action.reason(),
                action == null ? null : action.durationSeconds(),
                clock.instant()
        );
        publishModerationActionApplied(payload);
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

    private long requiredPostVersion(PostPayload payload) {
        long version = payload == null ? 0L : payload.aggregateVersion();
        if (version <= 0L) {
            throw new IllegalStateException("content post aggregate version missing");
        }
        return version;
    }
}
