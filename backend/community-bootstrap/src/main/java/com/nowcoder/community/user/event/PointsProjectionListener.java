package com.nowcoder.community.user.event;

import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.content.api.event.payload.CommentPayload;
import com.nowcoder.community.content.api.event.payload.PostPayload;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.social.api.event.SocialEventTypes;
import com.nowcoder.community.social.api.event.payload.LikePayload;
import com.nowcoder.community.social.event.SocialLocalEvent;
import com.nowcoder.community.user.service.PointsService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PointsProjectionListener {

    private final PointsService pointsService;

    public PointsProjectionListener(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentLocalEvent event) {
        if (event == null) {
            return;
        }
        if (ContentEventTypes.POST_PUBLISHED.equals(event.type()) && event.payload() instanceof PostPayload payload) {
            pointsService.applyPoints(payload.getUserId(), event.eventId(), event.type(), 10);
            return;
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
            pointsService.applyPoints(payload.getUserId(), event.eventId(), event.type(), 2);
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
        if (SocialEventTypes.LIKE_CREATED.equals(event.type())) {
            pointsService.applyPoints(toUserId, event.eventId(), event.type(), 1);
            return;
        }
        if (SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
            pointsService.applyPoints(toUserId, event.eventId(), event.type(), -1);
        }
    }
}
