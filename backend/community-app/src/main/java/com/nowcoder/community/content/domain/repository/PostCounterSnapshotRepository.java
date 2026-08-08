package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.PostCounterSnapshot;

import java.util.UUID;

public interface PostCounterSnapshotRepository {

    PostCounterSnapshot findByPostId(UUID postId);

    void upsert(
            UUID postId,
            long viewCount,
            long likeCount,
            long commentCount,
            long bookmarkCount,
            double score,
            long revision
    );
}
