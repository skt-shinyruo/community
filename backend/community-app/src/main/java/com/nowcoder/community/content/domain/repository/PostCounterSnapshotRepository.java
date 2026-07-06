package com.nowcoder.community.content.domain.repository;

import java.util.UUID;

public interface PostCounterSnapshotRepository {

    void upsert(
            UUID postId,
            long viewCount,
            long likeCount,
            long commentCount,
            long bookmarkCount,
            double score
    );
}
