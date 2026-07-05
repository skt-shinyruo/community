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
        UserRewardApplicationService.RewardCommand command =
                userRewardApplicationService.commandForPostPublished(postId, userId);
        if (command == null) {
            return;
        }
        userRewardApplicationService.apply(command);
    }

    @Override
    public void awardCommentCreated(UserCommentRewardRequest request) {
        if (request == null) {
            return;
        }
        UserRewardApplicationService.RewardCommand command = userRewardApplicationService.commandForCommentCreated(
                request.commentId(),
                request.userId()
        );
        if (command == null) {
            return;
        }
        userRewardApplicationService.apply(command);
    }

    @Override
    public void awardLikeCreated(UserLikeRewardRequest request) {
        if (request == null || !StringUtils.hasText(request.sourceEventId())) {
            return;
        }
        UserRewardApplicationService.RewardCommand command = userRewardApplicationService.commandForLikeCreated(
                request.sourceEventId(),
                request.actorUserId(),
                request.entityUserId()
        );
        if (command == null) {
            return;
        }
        userRewardApplicationService.apply(command);
    }

    @Override
    public void awardLikeRemoved(UserLikeRewardRequest request) {
        if (request == null || !StringUtils.hasText(request.sourceEventId())) {
            return;
        }
        UserRewardApplicationService.RewardCommand command = userRewardApplicationService.commandForLikeRemoved(
                request.sourceEventId(),
                request.actorUserId(),
                request.entityUserId()
        );
        if (command == null) {
            return;
        }
        userRewardApplicationService.apply(command);
    }
}
