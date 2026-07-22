package com.nowcoder.community.content.application;

import com.nowcoder.community.common.tx.AfterCommitExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CommentCacheAfterCommit {

    private static final Logger log = LoggerFactory.getLogger(CommentCacheAfterCommit.class);

    private final PostCounterCache postCounterCache;
    private final CommentPageCache commentPageCache;

    public CommentCacheAfterCommit(PostCounterCache postCounterCache, CommentPageCache commentPageCache) {
        this.postCounterCache = postCounterCache;
        this.commentPageCache = commentPageCache;
    }

    public void incrementCommentCount(UUID postId, long delta) {
        runBestEffortAfterCommit(
                "incrementCommentCount",
                postId,
                delta,
                () -> postCounterCache.incrementCommentCount(postId, delta)
        );
    }

    public void evictCommentPages(UUID postId) {
        runBestEffortAfterCommit(
                "evictCommentPages",
                postId,
                0L,
                () -> commentPageCache.evictPost(postId)
        );
    }

    private void runBestEffortAfterCommit(String operation, UUID postId, long delta, Runnable action) {
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                action.run();
            } catch (RuntimeException ignored) {
                log.warn(
                        "[comment-cache] operation={} postId={} delta={} failed",
                        operation,
                        postId,
                        delta
                );
            }
        });
    }
}
