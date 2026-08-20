package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.contracts.event.SocialTypedEvent;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OutboxSocialDomainEventPublisher implements SocialDomainEventPublisher {

    private final SocialContractEventCodec contractEventCodec;
    private final JdbcOutboxEventStore store;
    private final String topic;
    private final UuidV7Generator idGenerator;

    public OutboxSocialDomainEventPublisher(
            SocialContractEventCodec contractEventCodec,
            JdbcOutboxEventStore store,
            @Value("${social.events.outbox-topic:eventbus.social}") String topic,
            UuidV7Generator idGenerator
    ) {
        this.contractEventCodec = contractEventCodec;
        this.store = store;
        this.topic = topic;
        this.idGenerator = idGenerator;
    }

    @Override
    public void publishLikeChanged(LikeChangedDomainEvent event) {
        if (event == null || event.actorUserId() == null || event.entityId() == null) {
            return;
        }
        String type = event.liked() ? SocialEventTypes.LIKE_CREATED : SocialEventTypes.LIKE_REMOVED;
        String relationKey = requiredRelationKey(type, event.relationKey());
        long relationVersion = requiredVersion(type, event.relationVersion());
        LikePayload payload = new LikePayload(
                event.actorUserId(),
                event.entityType(),
                event.entityId(),
                event.entityUserId(),
                event.postId(),
                relationKey,
                event.relationInstanceId(),
                relationVersion
        );

        Instant occurredAt = requiredOccurredAt(type, event.occurredAt());
        String eventId = event.liked()
                ? "se:like:created:" + idGenerator.next()
                : "se:like:removed:" + idGenerator.next();
        SocialTypedEvent typedEvent = event.liked()
                ? new SocialTypedEvent.LikeCreated(
                        eventId, event.entityId(), "entity", occurredAt, relationVersion, payload)
                : new SocialTypedEvent.LikeRemoved(
                        eventId, event.entityId(), "entity", occurredAt, relationVersion, payload);
        publish(typedEvent, event.entityType() + ":" + event.entityId());
    }

    @Override
    public void publishFollowCreated(FollowCreatedDomainEvent event) {
        if (event == null || event.actorUserId() == null || event.entityId() == null) {
            return;
        }
        FollowPayload payload = new FollowPayload(
                event.actorUserId(),
                event.entityType(),
                event.entityId(),
                event.entityUserId(),
                event.createTime()
        );

        Instant occurredAt = requiredOccurredAt(SocialEventTypes.FOLLOW_CREATED, event.createTime());
        publish(new SocialTypedEvent.FollowCreated(
                "se:follow:created:" + idGenerator.next(),
                event.entityId(),
                "entity",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        ), event.entityType() + ":" + event.entityId());
    }

    @Override
    public void publishBlockRelationChanged(BlockRelationChangedDomainEvent event) {
        if (event == null || event.blockerUserId() == null || event.blockedUserId() == null) {
            return;
        }
        Instant occurredAt = requiredOccurredAt(SocialEventTypes.BLOCK_RELATION_CHANGED, event.occurredAt());
        long version = requiredVersion(SocialEventTypes.BLOCK_RELATION_CHANGED, event.version());
        BlockPayload payload = new BlockPayload(
                event.blockerUserId(),
                event.blockedUserId(),
                event.blocked(),
                occurredAt,
                version
        );

        publish(new SocialTypedEvent.BlockRelationChanged(
                "se:block:" + idGenerator.next(),
                event.blockerUserId(),
                "user",
                occurredAt,
                version,
                payload
        ), event.blockerUserId().toString());
    }

    private void publish(SocialTypedEvent event, String key) {
        String payloadJson;
        try {
            payloadJson = contractEventCodec.serialize(event);
        } catch (JsonCodecException e) {
            throw new IllegalStateException(
                    "social event outbox payload serialization failed: " + event.getClass().getSimpleName(), e);
        }
        store.enqueue(event.eventId(), topic, key, payloadJson);
    }

    private Instant requiredOccurredAt(String type, Instant occurredAt) {
        if (occurredAt == null) {
            throw new IllegalStateException("social event source occurredAt missing: " + type);
        }
        return occurredAt;
    }

    private long requiredVersion(String type, long version) {
        if (version <= 0L) {
            throw new IllegalStateException("social event source version missing: " + type);
        }
        return version;
    }

    private String requiredRelationKey(String type, String relationKey) {
        if (relationKey == null || relationKey.isBlank()) {
            throw new IllegalStateException("social event source relationKey missing: " + type);
        }
        return relationKey.trim();
    }

    private long positiveVersion(Instant occurredAt) {
        return Math.max(1L, occurredAt.toEpochMilli());
    }
}
