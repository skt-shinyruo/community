package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "social.events.publisher", havingValue = "local")
public class LocalSocialDomainEventPublisher implements SocialDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public LocalSocialDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
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
        publish(event.liked() ? SocialEventTypes.LIKE_CREATED : SocialEventTypes.LIKE_REMOVED, payload);
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
                UUID.randomUUID().toString(),
                SocialEventTypes.FOLLOW_CREATED,
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
                UUID.randomUUID().toString(),
                SocialEventTypes.BLOCK_RELATION_CHANGED,
                event.blockerUserId(),
                "user",
                occurredAt,
                version,
                payload
        );
    }

    private void publish(String type, LikePayload payload) {
        Instant occurredAt = requiredOccurredAt(type, payload.getOccurredAt());
        publish(
                UUID.randomUUID().toString(),
                type,
                payload.getEntityId(),
                "entity",
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
        applicationEventPublisher.publishEvent(new SocialContractEvent(
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
