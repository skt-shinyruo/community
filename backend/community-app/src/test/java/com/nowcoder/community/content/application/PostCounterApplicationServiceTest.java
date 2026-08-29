package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import com.nowcoder.community.content.application.PostCounterApplicationService.RecordPostViewCommand;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.PostCounterSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostCounterApplicationServiceTest {

    private PostCounterCache postCounterCache;
    private PostCounterApplicationService service;

    @BeforeEach
    void setUp() {
        postCounterCache = mock(PostCounterCache.class);
        service = newService(postCounterCache);
    }

    @Test
    void recordViewShouldDelegateInitializationDeduplicationAndIncrementToOneAtomicCacheOperation() {
        UUID postId = uuid(300);
        Instant viewedAt = Instant.parse("2026-07-06T10:00:00Z");
        RecordPostViewCommand command = new RecordPostViewCommand(postId, "viewer:aaa", viewedAt);
        PostCounterSnapshot cached = new PostCounterSnapshot(postId, 12L, 3L, 4L, 5L, 6.0);
        when(postCounterCache.get(postId)).thenReturn(cached);
        when(postCounterCache.isInitialized(postId)).thenReturn(true);

        service.recordView(command);
        service.recordView(command);

        verify(postCounterCache, times(2)).recordView(postId, "viewer:aaa", viewedAt, cached);
    }

    @Test
    void readShouldRestoreViewSnapshotAndAuthoritativeBookmarkCountAfterCacheLoss() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        PostContentRepository postRepository = mock(PostContentRepository.class);
        SocialLikeQueryApi likeQueryPort = mock(SocialLikeQueryApi.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        PostCounterApplicationService recoveryService = newService(
                postCounterCache,
                snapshotRepository,
                postRepository,
                likeQueryPort,
                bookmarkRepository
        );
        UUID postId = uuid(302);
        PostCounterSnapshot empty = new PostCounterSnapshot(postId, 0L, 0L, 0L, 0L, 0.0);
        PostCounterSnapshot baseline = new PostCounterSnapshot(postId, 41L, 9L, 5L, 7L, 99.5, 13L);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setCommentCount(5);
        post.setScore(99.5);
        when(postCounterCache.get(postId)).thenReturn(empty, baseline);
        when(postCounterCache.isInitialized(postId)).thenReturn(false);
        when(snapshotRepository.findByPostId(postId))
                .thenReturn(new PostCounterSnapshot(postId, 41L, 8L, 4L, 2L, 90.0, 13L));
        when(postRepository.getByIdAllowDeleted(postId)).thenReturn(post);
        when(likeQueryPort.count(EntityTypes.POST, postId)).thenReturn(9L);
        when(bookmarkRepository.countByPostId(postId)).thenReturn(7L);

        PostCounterSnapshot result = recoveryService.read(postId);

        assertThat(result).isEqualTo(baseline);
        verify(postCounterCache).initializeIfAbsent(baseline);
    }

    @Test
    void transientPersistentBaselineFailureShouldNotPoisonCacheAndNextViewShouldRecover() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        PostCounterApplicationService recoveryService = newService(
                postCounterCache,
                snapshotRepository,
                null,
                null
        );
        UUID postId = uuid(310);
        PostCounterSnapshot empty = new PostCounterSnapshot(postId, 0L, 0L, 0L, 0L, 0.0, 0L);
        PostCounterSnapshot persisted = new PostCounterSnapshot(postId, 41L, 0L, 0L, 0L, 0.0, 13L);
        Instant viewedAt = Instant.parse("2026-07-06T10:00:00Z");
        when(postCounterCache.get(postId)).thenReturn(empty);
        when(postCounterCache.isInitialized(postId)).thenReturn(false);
        when(snapshotRepository.findByPostId(postId))
                .thenThrow(new IllegalStateException("snapshot store unavailable"))
                .thenReturn(persisted);

        PostCounterSnapshot degraded = recoveryService.read(postId);

        assertThat(degraded).isEqualTo(empty);
        verify(postCounterCache, never()).initializeIfAbsent(any());
        verify(postCounterCache, never()).recordView(any(), any(), any(), any());

        recoveryService.recordView(new RecordPostViewCommand(postId, "viewer:recovered", viewedAt));

        verify(postCounterCache).recordView(postId, "viewer:recovered", viewedAt, persisted);
        verify(snapshotRepository, times(2)).findByPostId(postId);
    }

    @Test
    void flushSnapshotsShouldCapRequestedDirtyBatchSizeAtFiveHundred() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        SocialLikeQueryApi likeQueryPort = mock(SocialLikeQueryApi.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        PostCounterApplicationService flushService = newService(
                postCounterCache,
                snapshotRepository,
                null,
                likeQueryPort,
                bookmarkRepository
        );
        UUID postId = uuid(301);
        PostCounterCache.DirtyPost dirtyPost = new PostCounterCache.DirtyPost(postId, 17L);
        when(postCounterCache.dirtyPosts(500)).thenReturn(List.of(dirtyPost));
        when(postCounterCache.get(postId)).thenReturn(new PostCounterSnapshot(postId, 11L, 3L, 5L, 2L, 99.5));
        when(postCounterCache.isInitialized(postId)).thenReturn(true);
        when(likeQueryPort.count(EntityTypes.POST, postId)).thenReturn(3L);
        when(bookmarkRepository.countByPostId(postId)).thenReturn(2L);

        int flushed = flushService.flushSnapshots(2_000);

        assertThat(flushed).isEqualTo(1);
        verify(postCounterCache).dirtyPosts(500);
        verify(snapshotRepository).upsert(postId, 11L, 3L, 5L, 2L, 99.5, 17L);
        verify(postCounterCache).clearDirtyPosts(List.of(dirtyPost));
    }

    @Test
    void readShouldFallBackToPersistedAndOwnerFactsWhenRedisIsUnavailable() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        PostContentRepository postRepository = mock(PostContentRepository.class);
        SocialLikeQueryApi likeQueryPort = mock(SocialLikeQueryApi.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        PostCounterApplicationService fallbackService = newService(
                postCounterCache,
                snapshotRepository,
                postRepository,
                likeQueryPort,
                bookmarkRepository
        );
        UUID postId = uuid(303);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setCommentCount(8);
        post.setScore(77.5);
        when(postCounterCache.get(postId)).thenThrow(new IllegalStateException("redis unavailable"));
        when(snapshotRepository.findByPostId(postId))
                .thenReturn(new PostCounterSnapshot(postId, 21L, 2L, 3L, 4L, 5.0, 19L));
        when(postRepository.getByIdAllowDeleted(postId)).thenReturn(post);
        when(likeQueryPort.count(EntityTypes.POST, postId)).thenReturn(9L);
        when(bookmarkRepository.countByPostId(postId)).thenReturn(6L);

        PostCounterSnapshot result = fallbackService.read(postId);

        assertThat(result).isEqualTo(new PostCounterSnapshot(postId, 21L, 9L, 8L, 6L, 77.5, 19L));
    }

    @Test
    void recordViewShouldFailOpenWhenRedisIsUnavailable() {
        UUID postId = uuid(304);
        when(postCounterCache.get(postId)).thenThrow(new IllegalStateException("redis unavailable"));

        assertThatCode(() -> service.recordView(new RecordPostViewCommand(
                postId,
                "viewer",
                Instant.EPOCH
        ))).doesNotThrowAnyException();
    }

    @Test
    void flushSnapshotsShouldUseOneAsMinimumDirtyBatchSize() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        PostCounterApplicationService flushService = newService(
                postCounterCache,
                snapshotRepository,
                null,
                null
        );
        when(postCounterCache.dirtyPosts(1)).thenReturn(List.of());

        int flushed = flushService.flushSnapshots(0);

        assertThat(flushed).isZero();
        verify(postCounterCache).dirtyPosts(1);
        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void flushSnapshotsShouldRetainDirtyMarkerWhenRedisSnapshotReadFails() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        UUID postId = uuid(305);
        PostCounterApplicationService flushService = flushService(
                snapshotRepository,
                null,
                null,
                null
        );
        prepareOneDirtyPost(postId);
        when(postCounterCache.get(postId)).thenThrow(new IllegalStateException("redis unavailable"));

        assertFlushFailureRetainsDirty(flushService, snapshotRepository);
    }

    @Test
    void flushSnapshotsShouldRetainDirtyMarkerWhenPostFactReadFails() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        PostContentRepository postRepository = mock(PostContentRepository.class);
        UUID postId = uuid(306);
        PostCounterApplicationService flushService = flushService(
                snapshotRepository,
                postRepository,
                null,
                null
        );
        prepareInitializedSnapshot(postId);
        when(postRepository.getByIdAllowDeleted(postId)).thenThrow(new IllegalStateException("post store unavailable"));

        assertFlushFailureRetainsDirty(flushService, snapshotRepository);
    }

    @Test
    void flushSnapshotsShouldRetainDirtyMarkerWhenLikeFactReadFails() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        PostContentRepository postRepository = mock(PostContentRepository.class);
        SocialLikeQueryApi likeQueryPort = mock(SocialLikeQueryApi.class);
        UUID postId = uuid(307);
        PostCounterApplicationService flushService = flushService(
                snapshotRepository,
                postRepository,
                likeQueryPort,
                null
        );
        prepareInitializedSnapshot(postId);
        when(postRepository.getByIdAllowDeleted(postId)).thenReturn(post(postId));
        when(likeQueryPort.count(EntityTypes.POST, postId)).thenThrow(new IllegalStateException("like store unavailable"));

        assertFlushFailureRetainsDirty(flushService, snapshotRepository);
    }

    @Test
    void flushSnapshotsShouldRetainDirtyMarkerWhenBookmarkFactReadFails() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        PostContentRepository postRepository = mock(PostContentRepository.class);
        SocialLikeQueryApi likeQueryPort = mock(SocialLikeQueryApi.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        UUID postId = uuid(308);
        PostCounterApplicationService flushService = flushService(
                snapshotRepository,
                postRepository,
                likeQueryPort,
                bookmarkRepository
        );
        prepareInitializedSnapshot(postId);
        when(postRepository.getByIdAllowDeleted(postId)).thenReturn(post(postId));
        when(likeQueryPort.count(EntityTypes.POST, postId)).thenReturn(3L);
        when(bookmarkRepository.countByPostId(postId))
                .thenThrow(new IllegalStateException("bookmark store unavailable"));

        assertFlushFailureRetainsDirty(flushService, snapshotRepository);
    }

    @Test
    void flushSnapshotsShouldRetainDirtyMarkerWhenPersistentBaselineReadFails() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        UUID postId = uuid(309);
        PostCounterApplicationService flushService = flushService(
                snapshotRepository,
                null,
                null,
                null
        );
        prepareOneDirtyPost(postId);
        when(postCounterCache.get(postId)).thenReturn(new PostCounterSnapshot(postId, 1L, 0L, 0L, 0L, 0.0));
        when(postCounterCache.isInitialized(postId)).thenReturn(false);
        when(snapshotRepository.findByPostId(postId))
                .thenThrow(new IllegalStateException("snapshot store unavailable"));

        assertFlushFailureRetainsDirty(flushService, snapshotRepository);
    }

    @Test
    void flushSnapshotsShouldIsolateOnePostFailureAndFlushHealthyRows() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        SocialLikeQueryApi likeQueryPort = mock(SocialLikeQueryApi.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        UUID failedPost = uuid(330);
        UUID healthyPost = uuid(331);
        PostCounterCache.DirtyPost failedDirty = new PostCounterCache.DirtyPost(failedPost, 11L);
        PostCounterCache.DirtyPost healthyDirty = new PostCounterCache.DirtyPost(healthyPost, 12L);
        when(postCounterCache.dirtyPosts(10)).thenReturn(List.of(failedDirty, healthyDirty));
        when(postCounterCache.isInitialized(failedPost)).thenReturn(true);
        when(postCounterCache.isInitialized(healthyPost)).thenReturn(true);
        when(postCounterCache.get(failedPost)).thenThrow(new IllegalStateException("redis unavailable"));
        when(postCounterCache.get(healthyPost)).thenReturn(
                new PostCounterSnapshot(healthyPost, 3L, 4L, 5L, 6L, 7.0)
        );
        when(likeQueryPort.count(EntityTypes.POST, healthyPost)).thenReturn(4L);
        when(bookmarkRepository.countByPostId(healthyPost)).thenReturn(6L);

        PostCounterApplicationService flushService = newService(
                postCounterCache,
                snapshotRepository,
                null,
                likeQueryPort,
                bookmarkRepository
        );

        assertThatThrownBy(() -> flushService.flushSnapshots(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");
        verify(snapshotRepository).upsert(healthyPost, 3L, 4L, 5L, 6L, 7.0, 12L);
        verify(postCounterCache).clearDirtyPosts(List.of(healthyDirty));
    }

    @Test
    void bookmarkReconciliationShouldMarkDirtyAndClearOnlyMatchingSnapshotRevision() {
        BookmarkCounterReconciliationPort reconciliationPort = mock(BookmarkCounterReconciliationPort.class);
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        UUID postId = uuid(320);
        BookmarkCounterReconciliationPort.PendingBookmarkCounter token =
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(postId, 7L);
        when(reconciliationPort.listPending(10)).thenReturn(List.of(token));
        when(bookmarkRepository.countByPostId(postId)).thenReturn(4L);
        when(snapshotRepository.findByPostId(postId))
                .thenReturn(new PostCounterSnapshot(postId, 1L, 2L, 3L, 4L, 5.0, 8L));
        when(reconciliationPort.clearIfRevision(postId, 7L)).thenReturn(true);

        PostCounterApplicationService reconciliationService = newService(
                postCounterCache,
                snapshotRepository,
                null,
                null,
                bookmarkRepository,
                reconciliationPort
        );

        assertThat(reconciliationService.reconcileBookmarkCounters(10)).isEqualTo(1);
        var ordered = inOrder(postCounterCache, reconciliationPort);
        ordered.verify(postCounterCache).markDirty(postId);
        ordered.verify(reconciliationPort).clearIfRevision(postId, 7L);
        verify(reconciliationPort, never()).deferIfRevision(any(), anyLong());
    }

    @Test
    void bookmarkReconciliationShouldRetainMismatchedSnapshotAndDirtyTheCache() {
        BookmarkCounterReconciliationPort reconciliationPort = mock(BookmarkCounterReconciliationPort.class);
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        UUID postId = uuid(321);
        BookmarkCounterReconciliationPort.PendingBookmarkCounter token =
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(postId, 9L);
        when(reconciliationPort.listPending(10)).thenReturn(List.of(token));
        when(bookmarkRepository.countByPostId(postId)).thenReturn(5L);
        when(snapshotRepository.findByPostId(postId))
                .thenReturn(new PostCounterSnapshot(postId, 1L, 2L, 3L, 4L, 5.0, 8L));

        PostCounterApplicationService reconciliationService = newService(
                postCounterCache,
                snapshotRepository,
                null,
                null,
                bookmarkRepository,
                reconciliationPort
        );

        assertThat(reconciliationService.reconcileBookmarkCounters(10)).isZero();
        verify(postCounterCache).markDirty(postId);
        verify(reconciliationPort, never()).clearIfRevision(any(), anyLong());
        verify(reconciliationPort).deferIfRevision(postId, 9L);
    }

    @Test
    void bookmarkReconciliationShouldIsolateOnePostFailureAndClampBatchToFiveHundred() {
        BookmarkCounterReconciliationPort reconciliationPort = mock(BookmarkCounterReconciliationPort.class);
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        UUID failedPost = uuid(322);
        UUID healthyPost = uuid(323);
        when(reconciliationPort.listPending(500)).thenReturn(List.of(
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(failedPost, 1L),
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(healthyPost, 2L)
        ));
        doThrow(new IllegalStateException("redis unavailable"))
                .when(postCounterCache).markDirty(failedPost);
        when(bookmarkRepository.countByPostId(failedPost)).thenReturn(6L);
        when(snapshotRepository.findByPostId(failedPost))
                .thenReturn(new PostCounterSnapshot(failedPost, 1L, 2L, 3L, 6L, 5.0, 1L));
        when(reconciliationPort.clearIfRevision(failedPost, 1L)).thenReturn(true);
        when(bookmarkRepository.countByPostId(healthyPost)).thenReturn(6L);
        when(snapshotRepository.findByPostId(healthyPost))
                .thenReturn(new PostCounterSnapshot(healthyPost, 1L, 2L, 3L, 6L, 5.0, 2L));
        when(reconciliationPort.clearIfRevision(healthyPost, 2L)).thenReturn(true);

        PostCounterApplicationService reconciliationService = newService(
                postCounterCache,
                snapshotRepository,
                null,
                null,
                bookmarkRepository,
                reconciliationPort
        );

        assertThat(reconciliationService.reconcileBookmarkCounters(2_000)).isEqualTo(1);
        verify(reconciliationPort).listPending(500);
        verify(reconciliationPort, never()).clearIfRevision(failedPost, 1L);
        verify(reconciliationPort).deferIfRevision(failedPost, 1L);
        verify(reconciliationPort).clearIfRevision(healthyPost, 2L);
    }

    @Test
    void bookmarkReconciliationShouldRetainTokenWhenBookmarkOrSnapshotDependencyFails() {
        BookmarkCounterReconciliationPort reconciliationPort = mock(BookmarkCounterReconciliationPort.class);
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        UUID postId = uuid(324);
        when(reconciliationPort.listPending(10)).thenReturn(List.of(
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(postId, 3L)
        ));
        when(bookmarkRepository.countByPostId(postId))
                .thenThrow(new IllegalStateException("bookmark store unavailable"));

        PostCounterApplicationService reconciliationService = newService(
                postCounterCache,
                snapshotRepository,
                null,
                null,
                bookmarkRepository,
                reconciliationPort
        );

        assertThat(reconciliationService.reconcileBookmarkCounters(10)).isZero();
        verify(postCounterCache).markDirty(postId);
        verify(reconciliationPort, never()).clearIfRevision(any(), anyLong());
        verify(reconciliationPort).deferIfRevision(postId, 3L);
    }

    @Test
    void bookmarkReconciliationShouldRetainTokenWhenSnapshotStoreFails() {
        BookmarkCounterReconciliationPort reconciliationPort = mock(BookmarkCounterReconciliationPort.class);
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        UUID postId = uuid(325);
        when(reconciliationPort.listPending(10)).thenReturn(List.of(
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(postId, 4L)
        ));
        when(bookmarkRepository.countByPostId(postId)).thenReturn(2L);
        when(snapshotRepository.findByPostId(postId))
                .thenThrow(new IllegalStateException("snapshot store unavailable"));

        PostCounterApplicationService reconciliationService = newService(
                postCounterCache,
                snapshotRepository,
                null,
                null,
                bookmarkRepository,
                reconciliationPort
        );

        assertThat(reconciliationService.reconcileBookmarkCounters(10)).isZero();
        verify(postCounterCache).markDirty(postId);
        verify(reconciliationPort, never()).clearIfRevision(any(), anyLong());
        verify(reconciliationPort).deferIfRevision(postId, 4L);
    }

    @Test
    void bookmarkReconciliationShouldContinueAfterBookmarkCountFailureOnOnePost() {
        BookmarkCounterReconciliationPort reconciliationPort = mock(BookmarkCounterReconciliationPort.class);
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        UUID failedPost = uuid(326);
        UUID healthyPost = uuid(327);
        when(reconciliationPort.listPending(10)).thenReturn(List.of(
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(failedPost, 1L),
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(healthyPost, 2L)
        ));
        when(bookmarkRepository.countByPostId(failedPost))
                .thenThrow(new IllegalStateException("bookmark store unavailable"));
        when(bookmarkRepository.countByPostId(healthyPost)).thenReturn(8L);
        when(snapshotRepository.findByPostId(healthyPost))
                .thenReturn(new PostCounterSnapshot(healthyPost, 1L, 2L, 3L, 8L, 5.0, 2L));
        when(reconciliationPort.clearIfRevision(healthyPost, 2L)).thenReturn(true);

        PostCounterApplicationService reconciliationService = newService(
                postCounterCache,
                snapshotRepository,
                null,
                null,
                bookmarkRepository,
                reconciliationPort
        );

        assertThat(reconciliationService.reconcileBookmarkCounters(10)).isEqualTo(1);
        verify(postCounterCache).markDirty(failedPost);
        verify(postCounterCache).markDirty(healthyPost);
        verify(reconciliationPort).deferIfRevision(failedPost, 1L);
        verify(reconciliationPort).clearIfRevision(healthyPost, 2L);
    }

    @Test
    void bookmarkReconciliationShouldContinueAfterSnapshotFailureOnOnePost() {
        BookmarkCounterReconciliationPort reconciliationPort = mock(BookmarkCounterReconciliationPort.class);
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        UUID failedPost = uuid(328);
        UUID healthyPost = uuid(329);
        when(reconciliationPort.listPending(10)).thenReturn(List.of(
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(failedPost, 1L),
                new BookmarkCounterReconciliationPort.PendingBookmarkCounter(healthyPost, 2L)
        ));
        when(bookmarkRepository.countByPostId(failedPost)).thenReturn(7L);
        when(snapshotRepository.findByPostId(failedPost))
                .thenThrow(new IllegalStateException("snapshot store unavailable"));
        when(bookmarkRepository.countByPostId(healthyPost)).thenReturn(9L);
        when(snapshotRepository.findByPostId(healthyPost))
                .thenReturn(new PostCounterSnapshot(healthyPost, 1L, 2L, 3L, 9L, 5.0, 2L));
        when(reconciliationPort.clearIfRevision(healthyPost, 2L)).thenReturn(true);

        PostCounterApplicationService reconciliationService = newService(
                postCounterCache,
                snapshotRepository,
                null,
                null,
                bookmarkRepository,
                reconciliationPort
        );

        assertThat(reconciliationService.reconcileBookmarkCounters(10)).isEqualTo(1);
        verify(postCounterCache).markDirty(failedPost);
        verify(postCounterCache).markDirty(healthyPost);
        verify(reconciliationPort).deferIfRevision(failedPost, 1L);
        verify(reconciliationPort).clearIfRevision(healthyPost, 2L);
    }

    @Test
    void bookmarkReconciliationShouldUseOneAsMinimumBatchSize() {
        BookmarkCounterReconciliationPort reconciliationPort = mock(BookmarkCounterReconciliationPort.class);
        when(reconciliationPort.listPending(1)).thenReturn(List.of());
        PostCounterApplicationService reconciliationService = newService(
                postCounterCache,
                mock(PostCounterSnapshotRepository.class),
                null,
                null,
                mock(BookmarkRepository.class),
                reconciliationPort
        );

        assertThat(reconciliationService.reconcileBookmarkCounters(0)).isZero();
        verify(reconciliationPort).listPending(1);
    }

    private PostCounterApplicationService flushService(
            PostCounterSnapshotRepository snapshotRepository,
            PostContentRepository postRepository,
            SocialLikeQueryApi likeQueryPort,
            BookmarkRepository bookmarkRepository
    ) {
        return newService(
                postCounterCache,
                snapshotRepository,
                postRepository,
                likeQueryPort,
                bookmarkRepository
        );
    }

    private static PostCounterApplicationService newService(PostCounterCache postCounterCache) {
        return newService(
                postCounterCache,
                mock(PostCounterSnapshotRepository.class),
                mock(PostContentRepository.class),
                mock(SocialLikeQueryApi.class),
                mock(BookmarkRepository.class),
                mock(BookmarkCounterReconciliationPort.class)
        );
    }

    private static PostCounterApplicationService newService(
            PostCounterCache postCounterCache,
            PostCounterSnapshotRepository snapshotRepository,
            PostContentRepository postContentRepository,
            SocialLikeQueryApi likeQueryPort
    ) {
        return newService(
                postCounterCache,
                dependencyOrMock(snapshotRepository, PostCounterSnapshotRepository.class),
                dependencyOrMock(postContentRepository, PostContentRepository.class),
                dependencyOrMock(likeQueryPort, SocialLikeQueryApi.class),
                mock(BookmarkRepository.class),
                mock(BookmarkCounterReconciliationPort.class)
        );
    }

    private static PostCounterApplicationService newService(
            PostCounterCache postCounterCache,
            PostCounterSnapshotRepository snapshotRepository,
            PostContentRepository postContentRepository,
            SocialLikeQueryApi likeQueryPort,
            BookmarkRepository bookmarkRepository
    ) {
        return newService(
                postCounterCache,
                dependencyOrMock(snapshotRepository, PostCounterSnapshotRepository.class),
                dependencyOrMock(postContentRepository, PostContentRepository.class),
                dependencyOrMock(likeQueryPort, SocialLikeQueryApi.class),
                dependencyOrMock(bookmarkRepository, BookmarkRepository.class),
                mock(BookmarkCounterReconciliationPort.class)
        );
    }

    private static PostCounterApplicationService newService(
            PostCounterCache postCounterCache,
            PostCounterSnapshotRepository snapshotRepository,
            PostContentRepository postContentRepository,
            SocialLikeQueryApi likeQueryPort,
            BookmarkRepository bookmarkRepository,
            BookmarkCounterReconciliationPort reconciliationPort
    ) {
        return new PostCounterApplicationService(
                postCounterCache,
                dependencyOrMock(snapshotRepository, PostCounterSnapshotRepository.class),
                dependencyOrMock(postContentRepository, PostContentRepository.class),
                dependencyOrMock(likeQueryPort, SocialLikeQueryApi.class),
                dependencyOrMock(bookmarkRepository, BookmarkRepository.class),
                dependencyOrMock(reconciliationPort, BookmarkCounterReconciliationPort.class)
        );
    }

    private static <T> T dependencyOrMock(T dependency, Class<T> type) {
        return dependency == null ? mock(type) : dependency;
    }

    private void prepareInitializedSnapshot(UUID postId) {
        prepareOneDirtyPost(postId);
        when(postCounterCache.get(postId))
                .thenReturn(new PostCounterSnapshot(postId, 11L, 3L, 5L, 2L, 99.5));
        when(postCounterCache.isInitialized(postId)).thenReturn(true);
    }

    private void prepareOneDirtyPost(UUID postId) {
        when(postCounterCache.dirtyPosts(10))
                .thenReturn(List.of(new PostCounterCache.DirtyPost(postId, 23L)));
    }

    private void assertFlushFailureRetainsDirty(
            PostCounterApplicationService flushService,
            PostCounterSnapshotRepository snapshotRepository
    ) {
        assertThatThrownBy(() -> flushService.flushSnapshots(10)).isInstanceOf(RuntimeException.class);
        verify(snapshotRepository, never()).upsert(
                any(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyDouble(),
                anyLong()
        );
        verify(postCounterCache, never()).clearDirtyPosts(any());
    }

    private static DiscussPost post(UUID postId) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setCommentCount(5);
        post.setScore(9.5);
        return post;
    }
}
