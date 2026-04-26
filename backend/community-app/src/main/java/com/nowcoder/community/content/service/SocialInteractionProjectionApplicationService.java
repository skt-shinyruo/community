package com.nowcoder.community.content.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SocialInteractionProjectionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SocialInteractionProjectionApplicationService.class);

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
            try {
                postScoreQueue.add(postId);
            } catch (RuntimeException e) {
                log.warn("[post-score] projection failed after commit (eventId={}, type={}, postId={}): {}",
                        event.eventId(), event.type(), postId, e.toString());
            }
        }
    }
}
