package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.domain.repository.PostCounterSnapshotRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.PostCounterSnapshotMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisPostCounterSnapshotRepository implements PostCounterSnapshotRepository {

    private static final String RANK_VERSION = "hot-v2";

    private final PostCounterSnapshotMapper postCounterSnapshotMapper;

    public MyBatisPostCounterSnapshotRepository(PostCounterSnapshotMapper postCounterSnapshotMapper) {
        this.postCounterSnapshotMapper = postCounterSnapshotMapper;
    }

    @Override
    public void upsert(
            UUID postId,
            long viewCount,
            long likeCount,
            long commentCount,
            long bookmarkCount,
            double score
    ) {
        if (postId == null) {
            return;
        }
        postCounterSnapshotMapper.upsertCounterSnapshot(
                postId,
                Math.max(0L, viewCount),
                Math.max(0L, likeCount),
                Math.max(0L, commentCount),
                Math.max(0L, bookmarkCount)
        );
        postCounterSnapshotMapper.upsertScoreSnapshot(postId, score, RANK_VERSION);
    }
}
