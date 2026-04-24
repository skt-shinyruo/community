package com.nowcoder.community.content.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SocialInteractionProjectionApplicationService {

    private final PostScoreQueue postScoreQueue;

    public SocialInteractionProjectionApplicationService(PostScoreQueue postScoreQueue) {
        this.postScoreQueue = postScoreQueue;
    }

    public void projectSocialEvent(SocialContractEvent event) {
        boolean supported = event != null
                && (SocialEventTypes.LIKE_CREATED.equals(event.type()) || SocialEventTypes.LIKE_REMOVED.equals(event.type()));
        if (!supported || !(event.payload() instanceof LikePayload payload) || payload.getEntityType() != EntityTypes.POST) {
            return;
        }
        UUID postId = payload.getPostId() != null ? payload.getPostId() : payload.getEntityId();
        if (postId != null) {
            postScoreQueue.add(postId);
        }
    }
}
