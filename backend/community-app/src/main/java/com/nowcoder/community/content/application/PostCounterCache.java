package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.PostCounterSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PostCounterCache {

    PostCounterSnapshot get(UUID postId);

    boolean isInitialized(UUID postId);

    void initializeIfAbsent(PostCounterSnapshot baseline);

    boolean recordView(
            UUID postId,
            String viewerKey,
            Instant viewedAt,
            PostCounterSnapshot initializationBaseline
    );

    void markDirty(UUID postId);

    List<DirtyPost> dirtyPosts(int limit);

    void clearDirtyPosts(List<DirtyPost> dirtyPosts);

    record DirtyPost(UUID postId, long revision) {
    }
}
