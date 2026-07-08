package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnExpression("'${social.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class OutboxSocialDomainEventPublisher implements SocialDomainEventPublisher {

    private final JsonCodec jsonCodec;
    private final JdbcOutboxEventStore store;
    private final String topic;
    private final UuidV7Generator idGenerator = new UuidV7Generator();

    public OutboxSocialDomainEventPublisher(
            JsonCodec jsonCodec,
            JdbcOutboxEventStore store,
            @Value("${social.events.outbox-topic:eventbus.social}") String topic
    ) {
        this.jsonCodec = jsonCodec;
        this.store = store;
        this.topic = topic;
    }

    @Override
    public void publishLikeChanged(LikeChangedDomainEvent event) {
        if (event == null || event.actorUserId() == null || event.entityId() == null) {
            return;
        }
        LikePayload payload = new LikePayload();
        payload.setActorUserId(event.actorUserId());
        payload.setEntityType(event.entityType());
        payload.setEntityId(event.entityId());
        payload.setEntityUserId(event.entityUserId());
        payload.setPostId(event.postId());
        payload.setRelationKey(event.relationKey());
        payload.setOccurredAt(event.occurredAt());
        payload.setCreateTime(event.occurredAt());

        String type = event.liked() ? SocialEventTypes.LIKE_CREATED : SocialEventTypes.LIKE_REMOVED;
        Instant occurredAt = requiredOccurredAt(type, event.occurredAt());
        publish(
                event.liked() ? "se:like:created:" + idGenerator.next() : "se:like:removed:" + idGenerator.next(),
                type,
                event.entityType() + ":" + event.entityId(),
                event.entityId(),
                "entity",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        );
    }

    @Override
    public void publishFollowCreated(FollowCreatedDomainEvent event) {
        if (event == null || event.actorUserId() == null || event.entityId() == null) {
            return;
        }
        FollowPayload payload = new FollowPayload();
        payload.setActorUserId(event.actorUserId());
        payload.setEntityType(event.entityType());
        payload.setEntityId(event.entityId());
        payload.setEntityUserId(event.entityUserId());
        payload.setCreateTime(event.createTime());

        Instant occurredAt = requiredOccurredAt(SocialEventTypes.FOLLOW_CREATED, event.createTime());
        publish(
                "se:follow:created:" + idGenerator.next(),
                SocialEventTypes.FOLLOW_CREATED,
                event.entityType() + ":" + event.entityId(),
                event.entityId(),
                "entity",
                occurredAt,
                positiveVersion(occurredAt),
                payload
        );
    }

    @Override
    public void publishBlockRelationChanged(BlockRelationChangedDomainEvent event) {
        if (event == null || event.blockerUserId() == null || event.blockedUserId() == null) {
            return;
        }
        BlockPayload payload = new BlockPayload();
        payload.setBlockerUserId(event.blockerUserId());
        payload.setBlockedUserId(event.blockedUserId());
        payload.setBlocked(event.blocked());
        Instant occurredAt = requiredOccurredAt(SocialEventTypes.BLOCK_RELATION_CHANGED, event.occurredAt());
        long version = requiredVersion(SocialEventTypes.BLOCK_RELATION_CHANGED, event.version());
        payload.setOccurredAt(occurredAt);
        payload.setVersion(version);

        publish(
                "se:block:" + idGenerator.next(),
                SocialEventTypes.BLOCK_RELATION_CHANGED,
                event.blockerUserId().toString(),
                event.blockerUserId(),
                "user",
                occurredAt,
                version,
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
            payloadJson = jsonCodec.toJson(new SocialContractEvent(
                    eventId,
                    aggregateId,
                    aggregateType,
                    type,
                    occurredAt,
                    version,
                    payload
            ));
        } catch (JsonCodecException e) {
            throw new IllegalStateException("social event outbox payload serialization failed: " + type, e);
        }
        store.enqueue(eventId, topic, key, payloadJson);
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

    private long positiveVersion(Instant occurredAt) {
        return Math.max(1L, occurredAt.toEpochMilli());
    }
}
