package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.action.UserRewardActionApi;
import com.nowcoder.community.user.api.model.UserCommentRewardRequest;
import com.nowcoder.community.user.api.model.UserLikeRewardRequest;
import com.nowcoder.community.user.application.UserRewardApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class UserRewardApiAdapter implements UserRewardActionApi {

    private final UserRewardApplicationService userRewardApplicationService;

    public UserRewardApiAdapter(UserRewardApplicationService userRewardApplicationService) {
        this.userRewardApplicationService = userRewardApplicationService;
    }

    @Override
    public void awardPostPublished(UUID postId, UUID userId) {
        userRewardApplicationService.apply(userRewardApplicationService.commandForPostPublished(postId, userId));
    }

    @Override
    public void awardCommentCreated(UserCommentRewardRequest request) {
        if (request == null) {
            return;
        }
        userRewardApplicationService.apply(userRewardApplicationService.commandForCommentCreated(
                request.commentId(),
                request.userId()
        ));
    }

    @Override
    public void awardLikeCreated(UserLikeRewardRequest request) {
        if (request == null || !StringUtils.hasText(request.sourceEventId())) {
            return;
        }
        userRewardApplicationService.apply(userRewardApplicationService.commandForLikeCreated(
                request.sourceEventId(),
                request.actorUserId(),
                request.entityUserId()
        ));
    }

    @Override
    public void awardLikeRemoved(UserLikeRewardRequest request) {
        if (request == null || !StringUtils.hasText(request.sourceEventId())) {
            return;
        }
        userRewardApplicationService.apply(userRewardApplicationService.commandForLikeRemoved(
                request.sourceEventId(),
                request.actorUserId(),
                request.entityUserId()
        ));
    }
}
