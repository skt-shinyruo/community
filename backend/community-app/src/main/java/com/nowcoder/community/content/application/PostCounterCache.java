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

    List<UUID> dirtyPostIds(int limit);

    void clearDirtyPostIds(List<UUID> postIds);
}
