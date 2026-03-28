package com.nowcoder.community.content.api.query;

import com.nowcoder.community.content.api.model.PostScanView;

public interface PostScanQueryApi {

    PostScanView scanPosts(int afterId, int limit);

    PostScanView.PostProjectionView getPostProjectionAllowDeleted(int postId);
}
