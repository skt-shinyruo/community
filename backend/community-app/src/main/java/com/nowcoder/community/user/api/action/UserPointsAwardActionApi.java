package com.nowcoder.community.user.api.action;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;

import java.util.UUID;

public interface UserPointsAwardActionApi {

    void awardPostPublished(UUID postId, UUID userId);

    void awardCommentCreated(CommentPayload payload);

    void awardLikeCreated(String sourceEventId, LikePayload payload);

    void awardLikeRemoved(String sourceEventId, LikePayload payload);
}
