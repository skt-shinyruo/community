package com.nowcoder.community.social.event;

import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;

public interface SocialEventPublisher {

    void publishLikeCreated(LikePayload payload);

    void publishLikeRemoved(LikePayload payload);

    void publishFollowCreated(FollowPayload payload);

    void publishBlockRelationChanged(BlockPayload payload);
}
