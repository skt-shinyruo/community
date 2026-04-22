package com.nowcoder.community.content.api.query;

import com.nowcoder.community.content.api.model.PostScanView;

import java.util.UUID;

public interface PostScanQueryApi {

    PostScanView scanPosts(UUID afterId, int limit);

    PostScanView.PostProjectionView getPostProjectionAllowDeleted(UUID postId);
}
