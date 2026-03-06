package com.nowcoder.community.social.event;

import com.nowcoder.community.social.api.event.SocialEventTypes;
import com.nowcoder.community.social.api.event.payload.BlockPayload;
import com.nowcoder.community.social.api.event.payload.FollowPayload;
import com.nowcoder.community.social.api.event.payload.LikePayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "social.events.publisher", havingValue = "local", matchIfMissing = true)
public class LocalSocialEventPublisher implements SocialEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public LocalSocialEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishLikeCreated(LikePayload payload) {
        publish(SocialEventTypes.LIKE_CREATED, payload);
    }

    @Override
    public void publishLikeRemoved(LikePayload payload) {
        publish(SocialEventTypes.LIKE_REMOVED, payload);
    }

    @Override
    public void publishFollowCreated(FollowPayload payload) {
        publish(SocialEventTypes.FOLLOW_CREATED, payload);
    }

    @Override
    public void publishBlockRelationChanged(BlockPayload payload) {
        publish(SocialEventTypes.BLOCK_RELATION_CHANGED, payload);
    }

    private void publish(String type, Object payload) {
        applicationEventPublisher.publishEvent(new SocialLocalEvent(UUID.randomUUID().toString(), type, payload));
    }
}
