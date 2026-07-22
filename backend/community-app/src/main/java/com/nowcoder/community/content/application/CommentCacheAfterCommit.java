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
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                postCounterCache.incrementCommentCount(postId, delta);
            } catch (RuntimeException ignored) {
                log.warn(
                        "[comment-cache] operation={} postId={} delta={} failed",
                        "incrementCommentCount",
                        postId,
                        delta
                );
            }
        });
    }

    public void evictCommentPages(UUID postId) {
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                commentPageCache.evictPost(postId);
            } catch (RuntimeException ignored) {
                log.warn(
                        "[comment-cache] operation={} postId={} delta={} failed",
                        "evictCommentPages",
                        postId,
                        0L
                );
            }
        });
    }
}
