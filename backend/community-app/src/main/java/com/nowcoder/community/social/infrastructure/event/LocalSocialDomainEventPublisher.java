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

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "social.events.publisher", havingValue = "local", matchIfMissing = true)
public class LocalSocialDomainEventPublisher implements SocialDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public LocalSocialDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishLikeChanged(LikeChangedDomainEvent event) {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(event.actorUserId());
        payload.setEntityType(event.entityType());
        payload.setEntityId(event.entityId());
        payload.setEntityUserId(event.entityUserId());
        payload.setPostId(event.postId());
        payload.setCreateTime(event.createTime());
        publish(event.liked() ? SocialEventTypes.LIKE_CREATED : SocialEventTypes.LIKE_REMOVED, payload);
    }

    @Override
    public void publishFollowCreated(FollowCreatedDomainEvent event) {
        FollowPayload payload = new FollowPayload();
        payload.setActorUserId(event.actorUserId());
        payload.setEntityType(event.entityType());
        payload.setEntityId(event.entityId());
        payload.setEntityUserId(event.entityUserId());
        payload.setCreateTime(event.createTime());
        publish(SocialEventTypes.FOLLOW_CREATED, payload);
    }

    @Override
    public void publishBlockRelationChanged(BlockRelationChangedDomainEvent event) {
        BlockPayload payload = new BlockPayload();
        payload.setBlockerUserId(event.blockerUserId());
        payload.setBlockedUserId(event.blockedUserId());
        payload.setBlocked(event.blocked());
        publish(SocialEventTypes.BLOCK_RELATION_CHANGED, payload);
    }

    private void publish(String type, Object payload) {
        applicationEventPublisher.publishEvent(new SocialContractEvent(UUID.randomUUID().toString(), type, payload));
    }
}
