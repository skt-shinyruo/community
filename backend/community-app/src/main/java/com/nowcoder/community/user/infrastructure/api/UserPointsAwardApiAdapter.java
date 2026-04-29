package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.action.UserPointsAwardActionApi;
import com.nowcoder.community.user.api.model.UserCommentPointsAwardRequest;
import com.nowcoder.community.user.api.model.UserLikePointsAwardRequest;
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
        userPointsApplicationService.project(userPointsApplicationService.commandForPostPublished(postId, userId));
    }

    @Override
    public void awardCommentCreated(UserCommentPointsAwardRequest request) {
        if (request == null) {
            return;
        }
        userPointsApplicationService.project(userPointsApplicationService.commandForCommentCreated(
                request.commentId(),
                request.userId()
        ));
    }

    @Override
    public void awardLikeCreated(UserLikePointsAwardRequest request) {
        if (request == null || !StringUtils.hasText(request.sourceEventId())) {
            return;
        }
        userPointsApplicationService.project(userPointsApplicationService.commandForLikeCreated(
                request.sourceEventId(),
                request.actorUserId(),
                request.entityUserId()
        ));
    }

    @Override
    public void awardLikeRemoved(UserLikePointsAwardRequest request) {
        if (request == null || !StringUtils.hasText(request.sourceEventId())) {
            return;
        }
        userPointsApplicationService.project(userPointsApplicationService.commandForLikeRemoved(
                request.sourceEventId(),
                request.actorUserId(),
                request.entityUserId()
        ));
    }
}
