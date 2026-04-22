package com.nowcoder.community.content.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class SocialInteractionProjectionListener {

    private final PostScoreQueue postScoreQueue;

    public SocialInteractionProjectionListener(PostScoreQueue postScoreQueue) {
        this.postScoreQueue = postScoreQueue;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialContractEvent event) {
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
