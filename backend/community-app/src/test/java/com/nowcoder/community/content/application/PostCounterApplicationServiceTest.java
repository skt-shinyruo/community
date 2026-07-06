package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.RecordPostViewCommand;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import com.nowcoder.community.content.domain.repository.PostCounterSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
        service = new PostCounterApplicationService(postCounterCache);
    }

    @Test
    void recordViewShouldDeduplicateWithinViewerWindow() {
        UUID postId = uuid(300);
        Instant viewedAt = Instant.parse("2026-07-06T10:00:00Z");
        RecordPostViewCommand command = new RecordPostViewCommand(postId, "viewer:aaa", viewedAt);
        when(postCounterCache.markViewerSeen(postId, "viewer:aaa", viewedAt)).thenReturn(true, false);

        service.recordView(command);
        service.recordView(command);

        verify(postCounterCache, times(1)).incrementViewCount(postId);
    }

    @Test
    void flushSnapshotsShouldCapRequestedDirtyBatchSizeAtFiveHundred() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        PostCounterApplicationService flushService = new PostCounterApplicationService(
                postCounterCache,
                snapshotRepository,
                null,
                null
        );
        UUID postId = uuid(301);
        when(postCounterCache.dirtyPostIds(500)).thenReturn(List.of(postId));
        when(postCounterCache.get(postId)).thenReturn(new PostCounterSnapshot(postId, 11L, 3L, 5L, 2L, 99.5));

        int flushed = flushService.flushSnapshots(2_000);

        assertThat(flushed).isEqualTo(1);
        verify(postCounterCache).dirtyPostIds(500);
        verify(snapshotRepository).upsert(postId, 11L, 3L, 5L, 2L, 99.5);
        verify(postCounterCache).clearDirtyPostIds(List.of(postId));
    }

    @Test
    void flushSnapshotsShouldUseOneAsMinimumDirtyBatchSize() {
        PostCounterSnapshotRepository snapshotRepository = mock(PostCounterSnapshotRepository.class);
        PostCounterApplicationService flushService = new PostCounterApplicationService(
                postCounterCache,
                snapshotRepository,
                null,
                null
        );
        when(postCounterCache.dirtyPostIds(1)).thenReturn(List.of());

        int flushed = flushService.flushSnapshots(0);

        assertThat(flushed).isZero();
        verify(postCounterCache).dirtyPostIds(1);
        verifyNoInteractions(snapshotRepository);
    }
}
