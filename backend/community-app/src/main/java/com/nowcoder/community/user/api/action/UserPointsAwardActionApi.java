package com.nowcoder.community.user.api.action;

import com.nowcoder.community.user.api.model.UserCommentPointsAwardRequest;
import com.nowcoder.community.user.api.model.UserLikePointsAwardRequest;

import java.util.UUID;

public interface UserPointsAwardActionApi {

    void awardPostPublished(UUID postId, UUID userId);

    void awardCommentCreated(UserCommentPointsAwardRequest request);

    void awardLikeCreated(UserLikePointsAwardRequest request);

    void awardLikeRemoved(UserLikePointsAwardRequest request);
}
