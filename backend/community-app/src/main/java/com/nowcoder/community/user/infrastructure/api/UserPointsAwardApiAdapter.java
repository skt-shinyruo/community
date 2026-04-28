package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.user.api.action.UserPointsAwardActionApi;
import com.nowcoder.community.user.application.UserPointsApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class UserPointsAwardApiAdapter implements UserPointsAwardActionApi {

    private final UserPointsApplicationService userPointsApplicationService;

    public UserPointsAwardApiAdapter(UserPointsApplicationService userPointsApplicationService) {
        this.userPointsApplicationService = userPointsApplicationService;
    }

    @Override
    public void awardPostPublished(UUID postId, UUID userId) {
        if (postId == null || userId == null) {
            return;
        }
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(userId);
        userPointsApplicationService.project(userPointsApplicationService.commandForContentEvent(
                new ContentContractEvent("post-published:" + postId, ContentEventTypes.POST_PUBLISHED, payload)
        ));
    }

    @Override
    public void awardCommentCreated(CommentPayload payload) {
        if (payload == null || payload.getCommentId() == null) {
            return;
        }
        userPointsApplicationService.project(userPointsApplicationService.commandForContentEvent(
                new ContentContractEvent("comment-created:" + payload.getCommentId(), ContentEventTypes.COMMENT_CREATED, payload)
        ));
    }

    @Override
    public void awardLikeCreated(String sourceEventId, LikePayload payload) {
        if (!StringUtils.hasText(sourceEventId) || payload == null) {
            return;
        }
        userPointsApplicationService.project(userPointsApplicationService.commandForSocialEvent(
                new SocialContractEvent(sourceEventId, SocialEventTypes.LIKE_CREATED, payload)
        ));
    }

    @Override
    public void awardLikeRemoved(String sourceEventId, LikePayload payload) {
        if (!StringUtils.hasText(sourceEventId) || payload == null) {
            return;
        }
        userPointsApplicationService.project(userPointsApplicationService.commandForSocialEvent(
                new SocialContractEvent(sourceEventId, SocialEventTypes.LIKE_REMOVED, payload)
        ));
    }
}
