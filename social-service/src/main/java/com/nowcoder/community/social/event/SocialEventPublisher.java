package com.nowcoder.community.social.event;

import com.nowcoder.community.common.event.payload.FollowPayload;
import com.nowcoder.community.common.event.payload.LikePayload;

public interface SocialEventPublisher {

    void publishLikeCreated(LikePayload payload);

    void publishFollowCreated(FollowPayload payload);
}

