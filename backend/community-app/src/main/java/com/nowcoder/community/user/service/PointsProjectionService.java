package com.nowcoder.community.user.service;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.api.action.GrowthGrantActionApi;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Service;

@Service
public class PointsProjectionService {

    private final GrowthGrantActionApi growthGrantActionApi;

    public PointsProjectionService(GrowthGrantActionApi growthGrantActionApi) {
        this.growthGrantActionApi = growthGrantActionApi;
    }

    public PointsProjectionCommand commandForContentEvent(ContentContractEvent event) {
        if (event == null) {
            return null;
        }
        if (ContentEventTypes.POST_PUBLISHED.equals(event.type()) && event.payload() instanceof PostPayload payload) {
            return new PointsProjectionCommand(payload.getUserId(), 10, event.eventId(), event.type());
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
            return new PointsProjectionCommand(payload.getUserId(), 2, event.eventId(), event.type());
        }
        return null;
    }

    public PointsProjectionCommand commandForSocialEvent(SocialContractEvent event) {
        if (event == null || !(event.payload() instanceof LikePayload payload)) {
            return null;
        }
        int toUserId = payload.getEntityUserId() == null ? 0 : payload.getEntityUserId();
        if (toUserId <= 0 || toUserId == payload.getActorUserId()) {
            return null;
        }
        if (SocialEventTypes.LIKE_CREATED.equals(event.type())) {
            return new PointsProjectionCommand(toUserId, 1, event.eventId(), event.type());
        }
        if (SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
            return new PointsProjectionCommand(toUserId, -1, event.eventId(), event.type());
        }
        return null;
    }

    public void project(PointsProjectionCommand command) {
        if (command == null || command.userId() <= 0 || command.delta() == 0) {
            return;
        }
        growthGrantActionApi.applyPointsProjection(
                command.userId(),
                command.sourceEventId().trim(),
                command.sourceEventType().trim(),
                command.delta()
        );
    }

    public record PointsProjectionCommand(
            int userId,
            int delta,
            String sourceEventId,
            String sourceEventType
    ) {
    }
}
