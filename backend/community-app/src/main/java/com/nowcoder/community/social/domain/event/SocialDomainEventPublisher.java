package com.nowcoder.community.social.domain.event;

public interface SocialDomainEventPublisher {

    void publishLikeChanged(LikeChangedDomainEvent event);

    void publishFollowCreated(FollowCreatedDomainEvent event);

    void publishBlockRelationChanged(BlockRelationChangedDomainEvent event);
}
