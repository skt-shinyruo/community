package com.nowcoder.community.content.application;

import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class BookmarkApplicationService {

    private final BookmarkRepository bookmarkRepository;
    private final PostCounterCache postCounterCache;
    private final BookmarkCounterReconciliationPort bookmarkCounterReconciliationPort;
    private final PostFeedSummaryLoader postFeedSummaryLoader;

    public BookmarkApplicationService(
            BookmarkRepository bookmarkRepository,
            PostCounterCache postCounterCache,
            BookmarkCounterReconciliationPort bookmarkCounterReconciliationPort,
            PostFeedSummaryLoader postFeedSummaryLoader
    ) {
        this.bookmarkRepository = bookmarkRepository;
        this.postCounterCache = postCounterCache;
        this.bookmarkCounterReconciliationPort = Objects.requireNonNull(
                bookmarkCounterReconciliationPort,
                "bookmarkCounterReconciliationPort"
        );
        this.postFeedSummaryLoader = Objects.requireNonNull(postFeedSummaryLoader, "postFeedSummaryLoader");
    }

    @Transactional
    public void add(UUID userId, UUID postId) {
        bookmarkRepository.add(userId, postId);
        recordBookmarkMutation(postId);
        markBookmarkCountDirty(postId);
    }

    @Transactional
    public void remove(UUID userId, UUID postId) {
        bookmarkRepository.remove(userId, postId);
        recordBookmarkMutation(postId);
        markBookmarkCountDirty(postId);
    }

    public List<PostSummaryResult> listBookmarkedPostSummaries(UUID userId, int page, int size) {
        List<DiscussPost> posts = bookmarkRepository.listBookmarkedPosts(userId, page, size);
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }

        return postFeedSummaryLoader.serveCurrentPosts(posts);
    }

    private void markBookmarkCountDirty(UUID postId) {
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                postCounterCache.markDirty(postId);
            } catch (RuntimeException ignored) {
                // Bookmark facts are durable; a later read or flush rebuilds the derived count.
            }
        });
    }

    private void recordBookmarkMutation(UUID postId) {
        bookmarkCounterReconciliationPort.recordMutation(postId);
    }
}
