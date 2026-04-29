package com.nowcoder.community.growth.api.action;

import com.nowcoder.community.growth.api.model.GrowthCommentTaskProgressRequest;
import com.nowcoder.community.growth.api.model.GrowthLikeTaskProgressRequest;

import java.time.Instant;
import java.util.UUID;

public interface GrowthTaskProgressActionApi {

    void triggerPostPublished(UUID postId, UUID userId, Instant createTime);

    void triggerCommentCreated(GrowthCommentTaskProgressRequest request);

    void triggerLikeCreated(GrowthLikeTaskProgressRequest request);
}
