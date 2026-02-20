package com.nowcoder.community.social.event;

import com.nowcoder.community.social.api.event.payload.FollowPayload;
import com.nowcoder.community.social.api.event.payload.LikePayload;
import com.nowcoder.community.social.api.event.payload.BlockPayload;

public interface SocialEventPublisher {

    void publishLikeCreated(LikePayload payload);

    void publishLikeRemoved(LikePayload payload);

    void publishFollowCreated(FollowPayload payload);

    void publishBlockRelationChanged(BlockPayload payload);
}
