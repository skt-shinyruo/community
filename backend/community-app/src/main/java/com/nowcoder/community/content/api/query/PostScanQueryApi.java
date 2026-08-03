package com.nowcoder.community.content.api.query;

import com.nowcoder.community.content.api.model.PostScanView;

import java.util.UUID;

public interface PostScanQueryApi {

    /**
     * Scans by the unsigned canonical 16-byte UUID order. The returned cursor is the last item id
     * and can be passed back unchanged; this ordering is stable across the signed UUID high bit.
     */
    PostScanView scanPosts(UUID afterId, int limit);

    PostScanView.PostProjectionView getPostProjectionAllowDeleted(UUID postId);
}
