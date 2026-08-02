package com.nowcoder.community.content.application;

import com.nowcoder.community.common.tx.AfterCommitExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PostCacheAfterCommit {

    private static final Logger log = LoggerFactory.getLogger(PostCacheAfterCommit.class);

    private final PostFeedCache postFeedCache;
    private final PostSummaryCache postSummaryCache;
    private final PostDetailCache postDetailCache;

    public PostCacheAfterCommit(
            PostFeedCache postFeedCache,
            PostSummaryCache postSummaryCache,
            PostDetailCache postDetailCache
    ) {
        this.postFeedCache = postFeedCache;
        this.postSummaryCache = postSummaryCache;
        this.postDetailCache = postDetailCache;
    }

    public void evict(UUID postId, long aggregateVersion) {
        runAfterCommit("feed-remove", postId, () -> postFeedCache.remove(postId, null, aggregateVersion));
        evictSummaryAndDetail(postId, aggregateVersion);
    }

    public void evictSummaryAndDetail(UUID postId, long aggregateVersion) {
        runAfterCommit("summary-evict", postId, () -> postSummaryCache.evictAll(List.of(postId), aggregateVersion));
        runAfterCommit("detail-evict", postId, () -> postDetailCache.evict(postId, aggregateVersion));
    }

    public void terminalEvict(UUID postId, UUID boardId, long aggregateVersion) {
        runAfterCommit(
                "feed-terminal-remove",
                postId,
                () -> postFeedCache.terminalRemove(postId, boardId, aggregateVersion)
        );
        runAfterCommit(
                "summary-terminal-evict",
                postId,
                () -> postSummaryCache.terminalEvict(postId, aggregateVersion)
        );
        runAfterCommit(
                "detail-terminal-evict",
                postId,
                () -> postDetailCache.terminalEvict(postId, aggregateVersion)
        );
    }

    private void runAfterCommit(String operation, UUID postId, Runnable action) {
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                log.warn("[post-cache] operation={} postId={} failed", operation, postId, exception);
            }
        });
    }
}
