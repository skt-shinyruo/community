package com.nowcoder.community.content.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.social.event.SocialEventTypes;
import com.nowcoder.community.social.event.payload.LikePayload;
import com.nowcoder.community.social.event.SocialLocalEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SocialInteractionProjectionListener {

    private final PostScoreQueue postScoreQueue;

    public SocialInteractionProjectionListener(PostScoreQueue postScoreQueue) {
        this.postScoreQueue = postScoreQueue;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialLocalEvent event) {
        boolean supported = event != null
                && (SocialEventTypes.LIKE_CREATED.equals(event.type()) || SocialEventTypes.LIKE_REMOVED.equals(event.type()));
        if (!supported || !(event.payload() instanceof LikePayload payload) || payload.getEntityType() != EntityTypes.POST) {
            return;
        }
        int postId = payload.getPostId() != null && payload.getPostId() > 0 ? payload.getPostId() : payload.getEntityId();
        if (postId > 0) {
            postScoreQueue.add(postId);
        }
    }
}
