package com.nowcoder.community.user.service;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class PointsAwardService {

    private final PointsProjectionService pointsProjectionService;

    public PointsAwardService(PointsProjectionService pointsProjectionService) {
        this.pointsProjectionService = pointsProjectionService;
    }

    public void awardPostPublished(UUID postId, UUID userId) {
        if (postId == null || userId == null) {
            return;
        }
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(userId);
        pointsProjectionService.project(pointsProjectionService.commandForContentEvent(
                new ContentContractEvent("post-published:" + postId, ContentEventTypes.POST_PUBLISHED, payload)
        ));
    }

    public void awardCommentCreated(CommentPayload payload) {
        if (payload == null || payload.getCommentId() == null) {
            return;
        }
        pointsProjectionService.project(pointsProjectionService.commandForContentEvent(
                new ContentContractEvent("comment-created:" + payload.getCommentId(), ContentEventTypes.COMMENT_CREATED, payload)
        ));
    }

    public void awardLikeCreated(String sourceEventId, LikePayload payload) {
        if (!StringUtils.hasText(sourceEventId) || payload == null) {
            return;
        }
        pointsProjectionService.project(pointsProjectionService.commandForSocialEvent(
                new SocialContractEvent(sourceEventId, SocialEventTypes.LIKE_CREATED, payload)
        ));
    }

    public void awardLikeRemoved(String sourceEventId, LikePayload payload) {
        if (!StringUtils.hasText(sourceEventId) || payload == null) {
            return;
        }
        pointsProjectionService.project(pointsProjectionService.commandForSocialEvent(
                new SocialContractEvent(sourceEventId, SocialEventTypes.LIKE_REMOVED, payload)
        ));
    }
}
