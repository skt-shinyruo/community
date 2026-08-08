package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.BookmarkCounterReconciliationPort;
import com.nowcoder.community.content.infrastructure.persistence.mapper.BookmarkCounterReconciliationMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisBookmarkCounterReconciliationAdapter implements BookmarkCounterReconciliationPort {

    private final BookmarkCounterReconciliationMapper mapper;

    public MyBatisBookmarkCounterReconciliationAdapter(BookmarkCounterReconciliationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void recordMutation(UUID postId) {
        if (postId != null) {
            mapper.upsertRevision(postId);
        }
    }

    @Override
    public List<PendingBookmarkCounter> listPending(int limit) {
        int safeLimit = Math.max(1, Math.min(500, limit));
        return mapper.selectPending(safeLimit).stream()
                .map(row -> new PendingBookmarkCounter(row.getPostId(), row.getRevision()))
                .toList();
    }

    @Override
    public boolean clearIfRevision(UUID postId, long revision) {
        return postId != null && revision > 0L && mapper.clearIfRevision(postId, revision) > 0;
    }

    @Override
    public boolean deferIfRevision(UUID postId, long revision) {
        return postId != null && revision > 0L && mapper.deferIfRevision(postId, revision) > 0;
    }
}
