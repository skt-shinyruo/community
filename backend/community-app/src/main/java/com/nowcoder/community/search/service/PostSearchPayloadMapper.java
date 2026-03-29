package com.nowcoder.community.search.service;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.event.payload.PostPayload;

/**
 * Maps content scan projections onto search payloads.
 */
public final class PostSearchPayloadMapper {

    private PostSearchPayloadMapper() {
    }

    public static PostPayload toPayload(PostScanView.PostProjectionView projection) {
        PostPayload payload = new PostPayload();
        payload.setPostId(projection.postId());
        payload.setUserId(projection.userId());
        payload.setCategoryId(projection.categoryId());
        payload.setTags(projection.tags());
        payload.setTitle(projection.title());
        payload.setContent(projection.content());
        payload.setType(projection.type());
        payload.setStatus(projection.status());
        payload.setCreateTime(projection.createTime());
        payload.setScore(projection.score());
        return payload;
    }
}
