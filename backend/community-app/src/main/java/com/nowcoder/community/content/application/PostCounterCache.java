package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.PostCounterSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PostCounterCache {

    PostCounterSnapshot get(UUID postId);

    boolean markViewerSeen(UUID postId, String viewerKey, Instant viewedAt);

    void incrementViewCount(UUID postId);

    void incrementLikeCount(UUID postId, long delta);

    void incrementCommentCount(UUID postId, long delta);

    void incrementBookmarkCount(UUID postId, long delta);

    void updateScore(UUID postId, double score);

    List<DirtyPost> dirtyPosts(int limit);

    void clearDirtyPosts(List<DirtyPost> dirtyPosts);

    record DirtyPost(UUID postId, long revision) {
    }
}
