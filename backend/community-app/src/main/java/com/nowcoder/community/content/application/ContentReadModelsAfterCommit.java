package com.nowcoder.community.content.application;

import com.nowcoder.community.common.tx.AfterCommitExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ContentReadModelsAfterCommit {

    private static final Logger log = LoggerFactory.getLogger(ContentReadModelsAfterCommit.class);

    private final PostCounterCache postCounterCache;
    private final CommentPageCache commentPageCache;
    private final PostCacheAfterCommit postCacheAfterCommit;

    public ContentReadModelsAfterCommit(
            PostCounterCache postCounterCache,
            CommentPageCache commentPageCache,
            PostCacheAfterCommit postCacheAfterCommit
    ) {
        this.postCounterCache = postCounterCache;
        this.commentPageCache = commentPageCache;
        this.postCacheAfterCommit = postCacheAfterCommit;
    }

    public void commentCreated(UUID postId, long aggregateVersion) {
        markCommentCountDirty(postId);
        evictCommentPages(postId);
        postCacheAfterCommit.evict(postId, aggregateVersion);
    }

    public void commentEdited(UUID postId, long aggregateVersion) {
        evictCommentPages(postId);
        postCacheAfterCommit.evictSummaryAndDetail(postId, aggregateVersion);
    }

    public void commentDeleted(UUID postId, long aggregateVersion) {
        markCommentCountDirty(postId);
        evictCommentPages(postId);
        postCacheAfterCommit.evict(postId, aggregateVersion);
    }

    public void postUpdated(UUID postId, long aggregateVersion) {
        postCacheAfterCommit.evict(postId, aggregateVersion);
    }

    public void postDeleted(UUID postId, UUID boardId, long aggregateVersion) {
        postCacheAfterCommit.terminalEvict(postId, boardId, aggregateVersion);
    }

    private void markCommentCountDirty(UUID postId) {
        runBestEffortAfterCommit("comment-count-dirty", postId, () -> postCounterCache.markDirty(postId));
    }

    private void evictCommentPages(UUID postId) {
        runBestEffortAfterCommit("comment-pages-evict", postId, () -> commentPageCache.evictPost(postId));
    }

    private void runBestEffortAfterCommit(String operation, UUID postId, Runnable action) {
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                action.run();
            } catch (RuntimeException ignored) {
                log.warn("[content-read-models] operation={} postId={} failed", operation, postId);
            }
        });
    }
}
