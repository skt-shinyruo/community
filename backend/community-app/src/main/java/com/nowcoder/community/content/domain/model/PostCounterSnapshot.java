package com.nowcoder.community.content.domain.model;

import java.util.UUID;

public record PostCounterSnapshot(
        UUID postId,
        long viewCount,
        long likeCount,
        long commentCount,
        long bookmarkCount,
        double score
) {
    public PostCounterSnapshot {
        viewCount = Math.max(0L, viewCount);
        likeCount = Math.max(0L, likeCount);
        commentCount = Math.max(0L, commentCount);
        bookmarkCount = Math.max(0L, bookmarkCount);
        score = Math.max(0.0, score);
    }

    public static PostCounterSnapshot empty() {
        return new PostCounterSnapshot(null, 0L, 0L, 0L, 0L, 0.0);
    }
}
