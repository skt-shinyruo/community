package com.nowcoder.community.user.event;

import com.nowcoder.community.content.event.ContentEventTypes;
import com.nowcoder.community.content.event.payload.CommentPayload;
import com.nowcoder.community.content.event.payload.PostPayload;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.growth.api.action.GrowthGrantActionApi;
import com.nowcoder.community.social.event.SocialEventTypes;
import com.nowcoder.community.social.event.payload.LikePayload;
import com.nowcoder.community.social.event.SocialLocalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "false", matchIfMissing = true)
public class PointsProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(PointsProjectionListener.class);

    private final GrowthGrantActionApi growthGrantActionApi;

    public PointsProjectionListener(GrowthGrantActionApi growthGrantActionApi) {
        this.growthGrantActionApi = growthGrantActionApi;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentLocalEvent event) {
        if (event == null) {
            return;
        }
        try {
            if (ContentEventTypes.POST_PUBLISHED.equals(event.type()) && event.payload() instanceof PostPayload payload) {
                growthGrantActionApi.applyPointsProjection(payload.getUserId(), event.eventId(), event.type(), 10);
                return;
            }
            if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
                growthGrantActionApi.applyPointsProjection(payload.getUserId(), event.eventId(), event.type(), 2);
            }
        } catch (RuntimeException e) {
            log.warn("[points] projection failed after commit (eventId={}, type={}): {}", event.eventId(), event.type(), e.toString());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialLocalEvent event) {
        if (event == null || !(event.payload() instanceof LikePayload payload)) {
            return;
        }
        int toUserId = payload.getEntityUserId() == null ? 0 : payload.getEntityUserId();
        if (toUserId <= 0 || toUserId == payload.getActorUserId()) {
            return;
        }
        try {
            if (SocialEventTypes.LIKE_CREATED.equals(event.type())) {
                growthGrantActionApi.applyPointsProjection(toUserId, event.eventId(), event.type(), 1);
                return;
            }
            if (SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
                growthGrantActionApi.applyPointsProjection(toUserId, event.eventId(), event.type(), -1);
            }
        } catch (RuntimeException e) {
            log.warn("[points] projection failed after commit (eventId={}, type={}): {}", event.eventId(), event.type(), e.toString());
        }
    }
}
