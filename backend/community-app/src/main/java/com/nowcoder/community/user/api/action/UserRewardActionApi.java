package com.nowcoder.community.user.api.action;

import com.nowcoder.community.user.api.model.UserCommentRewardRequest;
import com.nowcoder.community.user.api.model.UserLikeRewardRequest;

import java.util.UUID;

public interface UserRewardActionApi {

    void awardPostPublished(UUID postId, UUID userId);

    void awardCommentCreated(UserCommentRewardRequest request);

    void awardLikeCreated(UserLikeRewardRequest request);

    void awardLikeRemoved(UserLikeRewardRequest request);
}
