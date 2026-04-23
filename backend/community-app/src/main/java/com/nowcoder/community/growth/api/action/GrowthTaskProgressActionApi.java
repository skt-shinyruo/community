package com.nowcoder.community.growth.api.action;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;

import java.time.Instant;
import java.util.UUID;

public interface GrowthTaskProgressActionApi {

    void triggerPostPublished(UUID postId, UUID userId, Instant createTime);

    void triggerCommentCreated(CommentPayload payload);

    void triggerLikeCreated(String sourceEventId, LikePayload payload);
}
