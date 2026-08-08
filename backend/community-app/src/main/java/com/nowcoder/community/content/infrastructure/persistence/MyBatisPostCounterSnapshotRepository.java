package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.ContentFeedPolicyProperties;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import com.nowcoder.community.content.domain.repository.PostCounterSnapshotRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.PostCounterSnapshotMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisPostCounterSnapshotRepository implements PostCounterSnapshotRepository {

    private final PostCounterSnapshotMapper postCounterSnapshotMapper;
    private final ContentFeedPolicyProperties policyProperties;

    public MyBatisPostCounterSnapshotRepository(
            PostCounterSnapshotMapper postCounterSnapshotMapper,
            ContentFeedPolicyProperties policyProperties
    ) {
        this.postCounterSnapshotMapper = postCounterSnapshotMapper;
        this.policyProperties = policyProperties;
    }

    @Override
    public PostCounterSnapshot findByPostId(UUID postId) {
        if (postId == null) {
            return null;
        }
        return postCounterSnapshotMapper.selectByPostId(postId);
    }

    @Override
    public void upsert(
            UUID postId,
            long viewCount,
            long likeCount,
            long commentCount,
            long bookmarkCount,
            double score,
            long revision
    ) {
        if (postId == null || revision <= 0L) {
            return;
        }
        postCounterSnapshotMapper.upsertCounterSnapshot(
                postId,
                Math.max(0L, viewCount),
                Math.max(0L, likeCount),
                Math.max(0L, commentCount),
                Math.max(0L, bookmarkCount),
                revision
        );
        postCounterSnapshotMapper.upsertScoreSnapshot(
                postId,
                score,
                policyProperties.getHotRankVersion(),
                revision
        );
    }
}
