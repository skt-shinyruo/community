package com.nowcoder.community.search.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.domain.model.PostSearchDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchReindexApplicationServiceTest {

    private static final Duration LEASE_TTL = Duration.ofMinutes(5);

    @Test
    void reindexShouldBuildFromAllContentPagesBeforePublishing() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchIndexRebuildPort rebuildPort = mock(SearchIndexRebuildPort.class);
        SearchReindexLeasePort leasePort = mock(SearchReindexLeasePort.class);
        SearchReindexLeasePort.Lease lease = mock(SearchReindexLeasePort.Lease.class);
        SearchIndexRebuildPort.RebuildSession session = mock(SearchIndexRebuildPort.RebuildSession.class);
        UUID firstId = uuid(101);
        UUID secondId = uuid(102);

        when(leasePort.tryAcquire(LEASE_TTL)).thenReturn(Optional.of(lease));
        when(lease.isValid()).thenReturn(true);
        when(rebuildPort.begin(LEASE_TTL)).thenReturn(session);
        when(session.isValid()).thenReturn(true);
        when(postScanQueryApi.scanPosts(null, 2)).thenReturn(new PostScanView(
                List.of(projection(firstId, 7L, 2L)), firstId, true
        ));
        when(postScanQueryApi.scanPosts(firstId, 2)).thenReturn(new PostScanView(
                List.of(projection(secondId, 8L, 3L)), secondId, false
        ));

        SearchReindexApplicationService.ReindexResult result = service(
                postScanQueryApi, rebuildPort, leasePort, 2
        ).reindex();

        assertThat(result.skipped()).isFalse();
        assertThat(result.indexedCount()).isEqualTo(2L);
        assertThat(result.executionId()).isNotBlank();
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<List<PostSearchDocument>> pageCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(session, org.mockito.Mockito.times(2)).appendPage(pageCaptor.capture());
        assertThat(pageCaptor.getAllValues().stream().flatMap(List::stream).toList())
                .extracting(PostSearchDocument::postId, PostSearchDocument::aggregateVersion, PostSearchDocument::scoreVersion)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(firstId, 7L, 2L),
                        org.assertj.core.groups.Tuple.tuple(secondId, 8L, 3L)
                );
        verify(postScanQueryApi).scanPosts(null, 2);
        verify(postScanQueryApi).scanPosts(firstId, 2);
        verify(session).publish();
        verify(session).close();
        verify(rebuildPort).cleanupRetiredIndices();
        verify(lease).close();
    }

    @Test
    void reindexShouldSkipWithoutTouchingContentWhenAnotherExecutionOwnsTheLease() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchIndexRebuildPort rebuildPort = mock(SearchIndexRebuildPort.class);
        SearchReindexLeasePort leasePort = mock(SearchReindexLeasePort.class);
        when(leasePort.tryAcquire(LEASE_TTL)).thenReturn(Optional.empty());

        SearchReindexApplicationService.ReindexResult result = service(
                postScanQueryApi, rebuildPort, leasePort, 10
        ).reindex();

        assertThat(result.skipped()).isTrue();
        assertThat(result.indexedCount()).isZero();
        assertThat(result.reason()).contains("lock unavailable");
        verifyNoInteractions(postScanQueryApi, rebuildPort);
    }

    @Test
    void reindexShouldRejectExecutionWhenOnlineProjectionIsDisabled() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchIndexRebuildPort rebuildPort = mock(SearchIndexRebuildPort.class);
        SearchReindexLeasePort leasePort = mock(SearchReindexLeasePort.class);
        SearchPolicyProperties properties = new SearchPolicyProperties();
        properties.setProjectionEnabled(false);
        SearchReindexApplicationService applicationService = new SearchReindexApplicationService(
                postScanQueryApi, rebuildPort, leasePort, 10, LEASE_TTL, properties, new UuidV7Generator()
        );

        assertThatThrownBy(applicationService::reindex)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("projection must be enabled");

        verifyNoInteractions(postScanQueryApi, rebuildPort, leasePort);
    }

    @Test
    void reindexShouldCloseUnpublishedSessionAndPreserveCurrentAliasWhenScanFails() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchIndexRebuildPort rebuildPort = mock(SearchIndexRebuildPort.class);
        SearchReindexLeasePort leasePort = mock(SearchReindexLeasePort.class);
        SearchReindexLeasePort.Lease lease = mock(SearchReindexLeasePort.Lease.class);
        SearchIndexRebuildPort.RebuildSession session = mock(SearchIndexRebuildPort.RebuildSession.class);
        RuntimeException failure = new RuntimeException("mysql unavailable");

        when(leasePort.tryAcquire(LEASE_TTL)).thenReturn(Optional.of(lease));
        when(lease.isValid()).thenReturn(true);
        when(rebuildPort.begin(LEASE_TTL)).thenReturn(session);
        when(session.isValid()).thenReturn(true);
        when(postScanQueryApi.scanPosts(null, 10)).thenThrow(failure);

        assertThatThrownBy(() -> service(postScanQueryApi, rebuildPort, leasePort, 10).reindex())
                .isSameAs(failure);

        verify(session).close();
        verify(session, never()).publish();
        verify(rebuildPort, never()).cleanupRetiredIndices();
        verify(lease).close();
    }

    @Test
    void reindexShouldAbortBeforePublicationWhenTargetLeaseIsLost() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchIndexRebuildPort rebuildPort = mock(SearchIndexRebuildPort.class);
        SearchReindexLeasePort leasePort = mock(SearchReindexLeasePort.class);
        SearchReindexLeasePort.Lease lease = mock(SearchReindexLeasePort.Lease.class);
        SearchIndexRebuildPort.RebuildSession session = mock(SearchIndexRebuildPort.RebuildSession.class);

        when(leasePort.tryAcquire(LEASE_TTL)).thenReturn(Optional.of(lease));
        when(lease.isValid()).thenReturn(true);
        when(rebuildPort.begin(LEASE_TTL)).thenReturn(session);
        when(session.isValid()).thenReturn(true, false);
        when(postScanQueryApi.scanPosts(null, 10)).thenReturn(new PostScanView(List.of(), null, false));

        assertThatThrownBy(() -> service(postScanQueryApi, rebuildPort, leasePort, 10).reindex())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("target lease was lost");

        verify(session, never()).publish();
        verify(session).close();
        verify(rebuildPort, never()).cleanupRetiredIndices();
    }

    @Test
    void reindexShouldRejectANonAdvancingContentCursor() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchIndexRebuildPort rebuildPort = mock(SearchIndexRebuildPort.class);
        SearchReindexLeasePort leasePort = mock(SearchReindexLeasePort.class);
        SearchReindexLeasePort.Lease lease = mock(SearchReindexLeasePort.Lease.class);
        SearchIndexRebuildPort.RebuildSession session = mock(SearchIndexRebuildPort.RebuildSession.class);

        when(leasePort.tryAcquire(LEASE_TTL)).thenReturn(Optional.of(lease));
        when(lease.isValid()).thenReturn(true);
        when(rebuildPort.begin(LEASE_TTL)).thenReturn(session);
        when(session.isValid()).thenReturn(true);
        when(postScanQueryApi.scanPosts(null, 10)).thenReturn(new PostScanView(
                List.of(projection(uuid(301), 1L, 0L)), null, true
        ));

        assertThatThrownBy(() -> service(postScanQueryApi, rebuildPort, leasePort, 10).reindex())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cursor did not advance");

        verify(session, never()).publish();
        verify(session).close();
    }

    @Test
    void reindexShouldUseUnsignedBinaryUuidOrderingAcrossTheHighBitBoundary() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchIndexRebuildPort rebuildPort = mock(SearchIndexRebuildPort.class);
        SearchReindexLeasePort leasePort = mock(SearchReindexLeasePort.class);
        SearchReindexLeasePort.Lease lease = mock(SearchReindexLeasePort.Lease.class);
        SearchIndexRebuildPort.RebuildSession session = mock(SearchIndexRebuildPort.RebuildSession.class);
        UUID lowerHalf = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        UUID upperHalf = UUID.fromString("80000000-0000-0000-0000-000000000000");

        when(leasePort.tryAcquire(LEASE_TTL)).thenReturn(Optional.of(lease));
        when(lease.isValid()).thenReturn(true);
        when(rebuildPort.begin(LEASE_TTL)).thenReturn(session);
        when(session.isValid()).thenReturn(true);
        when(postScanQueryApi.scanPosts(null, 1)).thenReturn(new PostScanView(
                List.of(projection(lowerHalf, 1L, 0L)), lowerHalf, true
        ));
        when(postScanQueryApi.scanPosts(lowerHalf, 1)).thenReturn(new PostScanView(
                List.of(projection(upperHalf, 1L, 0L)), upperHalf, false
        ));

        SearchReindexApplicationService.ReindexResult result = service(
                postScanQueryApi, rebuildPort, leasePort, 1
        ).reindex();

        assertThat(result.indexedCount()).isEqualTo(2L);
        verify(session).publish();
    }

    @Test
    void reindexShouldRejectACursorThatDoesNotMatchTheLastScannedItem() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchIndexRebuildPort rebuildPort = mock(SearchIndexRebuildPort.class);
        SearchReindexLeasePort leasePort = mock(SearchReindexLeasePort.class);
        SearchReindexLeasePort.Lease lease = mock(SearchReindexLeasePort.Lease.class);
        SearchIndexRebuildPort.RebuildSession session = mock(SearchIndexRebuildPort.RebuildSession.class);
        UUID itemId = uuid(401);

        when(leasePort.tryAcquire(LEASE_TTL)).thenReturn(Optional.of(lease));
        when(lease.isValid()).thenReturn(true);
        when(rebuildPort.begin(LEASE_TTL)).thenReturn(session);
        when(session.isValid()).thenReturn(true);
        when(postScanQueryApi.scanPosts(null, 10)).thenReturn(new PostScanView(
                List.of(projection(itemId, 1L, 0L)), uuid(402), false
        ));

        assertThatThrownBy(() -> service(postScanQueryApi, rebuildPort, leasePort, 10).reindex())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cursor did not advance");

        verify(session, never()).publish();
        verify(session).close();
    }

    private static SearchReindexApplicationService service(
            PostScanQueryApi postScanQueryApi,
            SearchIndexRebuildPort rebuildPort,
            SearchReindexLeasePort leasePort,
            int pageSize
    ) {
        return new SearchReindexApplicationService(
                postScanQueryApi,
                rebuildPort,
                leasePort,
                pageSize,
                LEASE_TTL,
                new SearchPolicyProperties(),
                new UuidV7Generator()
        );
    }

    private static PostScanView.PostProjectionView projection(
            UUID postId,
            long aggregateVersion,
            long scoreVersion
    ) {
        return new PostScanView.PostProjectionView(
                postId,
                uuid(7),
                uuid(3),
                List.of("java"),
                "title",
                "content",
                0,
                0,
                aggregateVersion,
                scoreVersion,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        );
    }
}
