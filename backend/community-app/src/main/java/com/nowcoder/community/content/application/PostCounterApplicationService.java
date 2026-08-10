package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.PostCounterSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PostCounterApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PostCounterApplicationService.class);

    private final PostCounterCache postCounterCache;
    private final PostCounterSnapshotRepository postCounterSnapshotRepository;
    private final PostContentRepository postContentRepository;
    private final LikeQueryPort likeQueryPort;
    private final BookmarkRepository bookmarkRepository;
    private final BookmarkCounterReconciliationPort bookmarkCounterReconciliationPort;

    public PostCounterApplicationService(
            PostCounterCache postCounterCache,
            PostCounterSnapshotRepository postCounterSnapshotRepository,
            PostContentRepository postContentRepository,
            LikeQueryPort likeQueryPort,
            BookmarkRepository bookmarkRepository,
            BookmarkCounterReconciliationPort bookmarkCounterReconciliationPort
    ) {
        this.postCounterCache = Objects.requireNonNull(postCounterCache, "postCounterCache must not be null");
        this.postCounterSnapshotRepository = Objects.requireNonNull(
                postCounterSnapshotRepository, "postCounterSnapshotRepository must not be null");
        this.postContentRepository = Objects.requireNonNull(
                postContentRepository, "postContentRepository must not be null");
        this.likeQueryPort = Objects.requireNonNull(likeQueryPort, "likeQueryPort must not be null");
        this.bookmarkRepository = Objects.requireNonNull(bookmarkRepository, "bookmarkRepository must not be null");
        this.bookmarkCounterReconciliationPort = Objects.requireNonNull(
                bookmarkCounterReconciliationPort, "bookmarkCounterReconciliationPort must not be null");
    }

    public void recordView(RecordPostViewCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.postId() == null || command.viewerKey() == null || command.viewerKey().isBlank()) {
            return;
        }
        try {
            UUID postId = command.postId();
            PostCounterSnapshot cached = defaultIfNull(postCounterCache.get(postId), postId);
            PostCounterSnapshot baseline = postCounterCache.isInitialized(postId)
                    ? cached
                    : rebuildBaseline(postId, cached, true);
            postCounterCache.recordView(postId, command.viewerKey(), command.viewedAt(), baseline);
        } catch (RuntimeException exception) {
            log.warn("[post-counter] view recording degraded postId={}", command.postId());
        }
    }

    public record RecordPostViewCommand(UUID postId, String viewerKey, java.time.Instant viewedAt) {
    }

    public void markDirty(UUID postId) {
        if (postId == null) {
            return;
        }
        try {
            postCounterCache.markDirty(postId);
        } catch (RuntimeException exception) {
            log.warn("[post-counter] dirty marking degraded postId={}", postId);
        }
    }

    private PostCounterSnapshot initializeAndGet(UUID postId) {
        PostCounterSnapshot cached = defaultIfNull(postCounterCache.get(postId), postId);
        if (postCounterCache.isInitialized(postId)) {
            return cached;
        }
        try {
            postCounterCache.initializeIfAbsent(rebuildBaseline(postId, cached, true));
        } catch (PersistentBaselineUnavailableException exception) {
            throw new CounterInitializationDeferredException(cached, exception);
        }
        return defaultIfNull(postCounterCache.get(postId), postId);
    }

    public PostCounterSnapshot read(UUID postId) {
        if (postId == null) {
            return PostCounterSnapshot.empty();
        }
        PostCounterSnapshot cached;
        try {
            cached = initializeAndGet(postId);
        } catch (CounterInitializationDeferredException exception) {
            log.warn("[post-counter] initialization deferred postId={}", postId);
            cached = exception.cachedSnapshot();
        } catch (RuntimeException exception) {
            log.warn("[post-counter] cache read degraded postId={}", postId);
            cached = rebuildBaseline(postId, PostCounterSnapshot.empty());
        }
        return overlayAuthoritativeFacts(postId, cached);
    }

    private PostCounterSnapshot rebuildBaseline(UUID postId, PostCounterSnapshot cached) {
        return rebuildBaseline(postId, cached, false);
    }

    private PostCounterSnapshot rebuildBaseline(
            UUID postId,
            PostCounterSnapshot cached,
            boolean requirePersistentBaseline
    ) {
        PostCounterSnapshot persisted = null;
        if (postCounterSnapshotRepository != null) {
            try {
                persisted = postCounterSnapshotRepository.findByPostId(postId);
            } catch (RuntimeException exception) {
                if (requirePersistentBaseline) {
                    throw new PersistentBaselineUnavailableException(exception);
                }
                log.warn("[post-counter] persisted baseline read degraded postId={}", postId);
            }
        }
        PostCounterSnapshot snapshot = defaultIfNull(persisted, postId);
        long viewCount = Math.max(cached.viewCount(), snapshot.viewCount());
        long likeCount = Math.max(cached.likeCount(), snapshot.likeCount());
        long commentCount = Math.max(cached.commentCount(), snapshot.commentCount());
        long bookmarkCount = Math.max(cached.bookmarkCount(), snapshot.bookmarkCount());
        double score = Math.max(cached.score(), snapshot.score());

        if (postContentRepository != null) {
            try {
                DiscussPost post = postContentRepository.getByIdAllowDeleted(postId);
                if (post != null) {
                    commentCount = Math.max(0L, post.getCommentCount());
                    score = Math.max(0.0, post.getScore());
                }
            } catch (RuntimeException ignored) {
                // A deleted/missing post can still have a persisted counter snapshot to flush.
            }
        }
        if (likeQueryPort != null) {
            try {
                likeCount = Math.max(0L, likeQueryPort.countPostLikes(postId));
            } catch (RuntimeException exception) {
                log.warn("[post-counter] like baseline read degraded postId={}", postId);
            }
        }
        if (bookmarkRepository != null) {
            try {
                bookmarkCount = Math.max(0L, bookmarkRepository.countByPostId(postId));
            } catch (RuntimeException exception) {
                log.warn("[post-counter] bookmark baseline read degraded postId={}", postId);
            }
        }
        return new PostCounterSnapshot(
                postId,
                viewCount,
                likeCount,
                commentCount,
                bookmarkCount,
                score,
                snapshot.revision()
        );
    }

    private PostCounterSnapshot overlayAuthoritativeFacts(UUID postId, PostCounterSnapshot baseline) {
        long likeCount = baseline.likeCount();
        long commentCount = baseline.commentCount();
        long bookmarkCount = baseline.bookmarkCount();
        double score = baseline.score();

        if (postContentRepository != null) {
            try {
                DiscussPost post = postContentRepository.getByIdAllowDeleted(postId);
                if (post != null) {
                    commentCount = Math.max(0L, post.getCommentCount());
                    score = Math.max(0.0, post.getScore());
                }
            } catch (RuntimeException exception) {
                log.warn("[post-counter] post fact read degraded postId={}", postId);
            }
        }
        if (likeQueryPort != null) {
            try {
                likeCount = Math.max(0L, likeQueryPort.countPostLikes(postId));
            } catch (RuntimeException exception) {
                log.warn("[post-counter] like fact read degraded postId={}", postId);
            }
        }
        if (bookmarkRepository != null) {
            try {
                bookmarkCount = Math.max(0L, bookmarkRepository.countByPostId(postId));
            } catch (RuntimeException exception) {
                log.warn("[post-counter] bookmark fact read degraded postId={}", postId);
            }
        }
        return new PostCounterSnapshot(
                postId,
                baseline.viewCount(),
                likeCount,
                commentCount,
                bookmarkCount,
                score,
                baseline.revision()
        );
    }

    public int flushSnapshots(int batchSize) {
        if (postCounterSnapshotRepository == null) {
            return 0;
        }
        int safeBatchSize = Math.max(1, Math.min(batchSize, 500));
        List<PostCounterCache.DirtyPost> requested = postCounterCache.dirtyPosts(safeBatchSize);
        if (requested == null || requested.isEmpty()) {
            return 0;
        }
        List<PostCounterCache.DirtyPost> flushed = new ArrayList<>();
        RuntimeException firstFailure = null;
        for (PostCounterCache.DirtyPost dirtyPost : requested) {
            if (dirtyPost == null || dirtyPost.postId() == null) {
                continue;
            }
            UUID postId = dirtyPost.postId();
            try {
                PostCounterSnapshot snapshot = readStrictlyForFlush(postId);
                postCounterSnapshotRepository.upsert(
                        postId,
                        snapshot.viewCount(),
                        snapshot.likeCount(),
                        snapshot.commentCount(),
                        snapshot.bookmarkCount(),
                        snapshot.score(),
                        dirtyPost.revision()
                );
                flushed.add(dirtyPost);
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                }
                log.warn("[post-counter] snapshot flush retained postId={}: {}", postId, exception.toString());
            }
        }
        if (!flushed.isEmpty()) {
            postCounterCache.clearDirtyPosts(flushed);
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        return flushed.size();
    }

    public int reconcileBookmarkCounters(int batchSize) {
        if (bookmarkCounterReconciliationPort == null
                || bookmarkRepository == null
                || postCounterSnapshotRepository == null) {
            return 0;
        }
        int safeBatchSize = Math.max(1, Math.min(batchSize, 500));
        List<BookmarkCounterReconciliationPort.PendingBookmarkCounter> pending;
        try {
            pending = bookmarkCounterReconciliationPort.listPending(safeBatchSize);
        } catch (RuntimeException exception) {
            log.warn("[post-counter] bookmark reconciliation scan degraded: {}", exception.toString());
            return 0;
        }
        if (pending == null || pending.isEmpty()) {
            return 0;
        }

        int cleared = 0;
        for (BookmarkCounterReconciliationPort.PendingBookmarkCounter token : pending) {
            if (token == null || token.postId() == null || token.revision() <= 0L) {
                continue;
            }
            UUID postId = token.postId();
            try {
                postCounterCache.markDirty(postId);
                long authoritativeCount = Math.max(0L, bookmarkRepository.countByPostId(postId));
                PostCounterSnapshot persisted = postCounterSnapshotRepository.findByPostId(postId);
                if (persisted != null
                        && persisted.bookmarkCount() == authoritativeCount
                        && bookmarkCounterReconciliationPort.clearIfRevision(postId, token.revision())) {
                    cleared++;
                } else {
                    deferBookmarkReconciliation(postId, token.revision());
                }
            } catch (RuntimeException exception) {
                deferBookmarkReconciliation(postId, token.revision());
                log.warn(
                        "[post-counter] bookmark reconciliation retained postId={} revision={}: {}",
                        postId,
                        token.revision(),
                        exception.toString()
                );
            }
        }
        return cleared;
    }

    private void deferBookmarkReconciliation(UUID postId, long revision) {
        try {
            bookmarkCounterReconciliationPort.deferIfRevision(postId, revision);
        } catch (RuntimeException exception) {
            log.warn(
                    "[post-counter] bookmark reconciliation retry rotation degraded postId={} revision={}: {}",
                    postId,
                    revision,
                    exception.toString()
            );
        }
    }

    private PostCounterSnapshot readStrictlyForFlush(UUID postId) {
        PostCounterSnapshot cached = defaultIfNull(postCounterCache.get(postId), postId);
        PostCounterSnapshot baseline = cached;
        if (!postCounterCache.isInitialized(postId)) {
            PostCounterSnapshot persisted = postCounterSnapshotRepository.findByPostId(postId);
            baseline = mergeBaselines(postId, cached, persisted);
            postCounterCache.initializeIfAbsent(baseline);
            baseline = defaultIfNull(postCounterCache.get(postId), postId);
        }
        return overlayAuthoritativeFactsStrictly(postId, baseline);
    }

    private PostCounterSnapshot overlayAuthoritativeFactsStrictly(UUID postId, PostCounterSnapshot baseline) {
        long likeCount = baseline.likeCount();
        long commentCount = baseline.commentCount();
        long bookmarkCount = baseline.bookmarkCount();
        double score = baseline.score();

        if (postContentRepository != null) {
            DiscussPost post = postContentRepository.getByIdAllowDeleted(postId);
            if (post != null) {
                commentCount = Math.max(0L, post.getCommentCount());
                score = Math.max(0.0, post.getScore());
            }
        }
        if (likeQueryPort != null) {
            likeCount = Math.max(0L, likeQueryPort.countPostLikes(postId));
        }
        if (bookmarkRepository != null) {
            bookmarkCount = Math.max(0L, bookmarkRepository.countByPostId(postId));
        }
        return new PostCounterSnapshot(
                postId,
                baseline.viewCount(),
                likeCount,
                commentCount,
                bookmarkCount,
                score,
                baseline.revision()
        );
    }

    private static PostCounterSnapshot mergeBaselines(
            UUID postId,
            PostCounterSnapshot cached,
            PostCounterSnapshot persisted
    ) {
        PostCounterSnapshot persistentBaseline = defaultIfNull(persisted, postId);
        return new PostCounterSnapshot(
                postId,
                Math.max(cached.viewCount(), persistentBaseline.viewCount()),
                Math.max(cached.likeCount(), persistentBaseline.likeCount()),
                Math.max(cached.commentCount(), persistentBaseline.commentCount()),
                Math.max(cached.bookmarkCount(), persistentBaseline.bookmarkCount()),
                Math.max(cached.score(), persistentBaseline.score()),
                Math.max(cached.revision(), persistentBaseline.revision())
        );
    }

    private static PostCounterSnapshot defaultIfNull(PostCounterSnapshot snapshot, UUID postId) {
        return snapshot == null ? new PostCounterSnapshot(postId, 0L, 0L, 0L, 0L, 0.0, 0L) : snapshot;
    }

    private static final class PersistentBaselineUnavailableException extends RuntimeException {

        private PersistentBaselineUnavailableException(RuntimeException cause) {
            super(cause);
        }
    }

    private static final class CounterInitializationDeferredException extends RuntimeException {

        private final PostCounterSnapshot cachedSnapshot;

        private CounterInitializationDeferredException(
                PostCounterSnapshot cachedSnapshot,
                PersistentBaselineUnavailableException cause
        ) {
            super(cause);
            this.cachedSnapshot = cachedSnapshot;
        }

        private PostCounterSnapshot cachedSnapshot() {
            return cachedSnapshot;
        }
    }
}
