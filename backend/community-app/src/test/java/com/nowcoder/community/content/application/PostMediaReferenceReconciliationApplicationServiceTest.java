package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.PostMediaReferenceQueryPort.RemoteReferenceStatus;
import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import com.nowcoder.community.content.application.command.ReconcilePostMediaReferencesCommand;
import com.nowcoder.community.content.application.result.PostMediaReferenceReconciliationResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostMediaReferenceOperation;
import com.nowcoder.community.content.domain.model.PostMediaReferenceStatus;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.domain.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostMediaReferenceReconciliationApplicationServiceTest {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Instant NOW = Instant.parse("2026-07-15T11:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void reconciliationBatchMustNotHoldOneTransactionAcrossRemoteChecksAndAllAssets() throws Exception {
        assertThat(PostMediaReferenceReconciliationApplicationService.class
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(PostMediaReferenceReconciliationApplicationService.class
                .getDeclaredMethod("reconcile", ReconcilePostMediaReferencesCommand.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    void pendingReferenceWithoutOutboxShouldRepublishItsCurrentOperationVersion() {
        Fixture fixture = fixture();
        PostMediaAsset pending = asset(
                uuid(601),
                uuid(301),
                PostMediaReferenceStatus.BIND_PENDING,
                3L
        );
        when(fixture.mediaRepository.scanReferenceStatesAfter(ZERO_UUID, 10))
                .thenReturn(List.of(pending));

        PostMediaReferenceReconciliationResult result = fixture.service.reconcile(command(10));

        verify(fixture.publisher).publish(new PostMediaReferenceCommand(
                pending.id(),
                PostMediaReferenceOperation.BIND,
                3L,
                pending.ownerUserId()
        ));
        verify(fixture.queryPort, never()).findReferenceStatus(any(), any());
        assertThat(result).isEqualTo(new PostMediaReferenceReconciliationResult(
                pending.id(), false, 1, 1, 0, 1, 0
        ));
        verify(fixture.metrics).setPendingReferences(1L);
        verify(fixture.metrics).setDriftedReferences(0L);
    }

    @Test
    void deletedPostWithBoundReferenceShouldScheduleDurableRelease() {
        Fixture fixture = fixture();
        PostMediaAsset bound = asset(uuid(602), uuid(302), PostMediaReferenceStatus.BOUND, 6L);
        when(fixture.mediaRepository.scanReferenceStatesAfter(ZERO_UUID, 10))
                .thenReturn(List.of(bound));
        when(fixture.postRepository.getRequiredSnapshot(bound.postId()))
                .thenReturn(new PostSnapshot(bound.postId(), bound.ownerUserId(), 2, Date.from(NOW)));
        when(fixture.mediaRepository.requestRelease(eq(bound.id()), any(Date.class))).thenReturn(7L);

        PostMediaReferenceReconciliationResult result = fixture.service.reconcile(command(10));

        verify(fixture.publisher).publish(new PostMediaReferenceCommand(
                bound.id(),
                PostMediaReferenceOperation.RELEASE,
                7L,
                bound.ownerUserId()
        ));
        verify(fixture.queryPort, never()).findReferenceStatus(any(), any());
        assertThat(result.drifted()).isOne();
        assertThat(result.scheduled()).isOne();
        assertThat(result.failed()).isZero();
    }

    @Test
    void locallyReleasedButRemotelyActiveReferenceShouldReopenReleaseIntent() {
        Fixture fixture = fixture();
        PostMediaAsset released = asset(uuid(603), uuid(303), PostMediaReferenceStatus.RELEASED, 8L);
        when(fixture.mediaRepository.scanReferenceStatesAfter(ZERO_UUID, 10))
                .thenReturn(List.of(released));
        when(fixture.queryPort.findReferenceStatus(released.ossObjectId(), released.ossReferenceId()))
                .thenReturn(RemoteReferenceStatus.ACTIVE);
        when(fixture.mediaRepository.requestReleaseRepair(eq(released.id()), any(Date.class)))
                .thenReturn(9L);

        PostMediaReferenceReconciliationResult result = fixture.service.reconcile(command(10));

        verify(fixture.publisher).publish(new PostMediaReferenceCommand(
                released.id(),
                PostMediaReferenceOperation.RELEASE,
                9L,
                released.ownerUserId()
        ));
        assertThat(result.drifted()).isOne();
        assertThat(result.scheduled()).isOne();
        assertThat(result.failed()).isZero();
    }

    @Test
    void locallyBoundButRemoteReferenceMissingShouldReopenBindIntent() {
        Fixture fixture = fixture();
        PostMediaAsset bound = asset(uuid(604), uuid(304), PostMediaReferenceStatus.BOUND, 10L);
        when(fixture.mediaRepository.scanReferenceStatesAfter(ZERO_UUID, 10))
                .thenReturn(List.of(bound));
        when(fixture.postRepository.getRequiredSnapshot(bound.postId()))
                .thenReturn(new PostSnapshot(bound.postId(), bound.ownerUserId(), 0, Date.from(NOW)));
        when(fixture.queryPort.findReferenceStatus(bound.ossObjectId(), bound.ossReferenceId()))
                .thenReturn(RemoteReferenceStatus.MISSING);
        when(fixture.mediaRepository.requestBindRepair(eq(bound.id()), any(Date.class))).thenReturn(11L);

        PostMediaReferenceReconciliationResult result = fixture.service.reconcile(command(10));

        verify(fixture.publisher).publish(new PostMediaReferenceCommand(
                bound.id(),
                PostMediaReferenceOperation.BIND,
                11L,
                bound.ownerUserId()
        ));
        assertThat(result.drifted()).isOne();
        assertThat(result.scheduled()).isOne();
        assertThat(result.failed()).isZero();
    }

    @Test
    void oneRemoteCheckFailureShouldNotBlockLaterPendingAssetOrCursorProgress() {
        Fixture fixture = fixture();
        PostMediaAsset failed = asset(uuid(605), uuid(305), PostMediaReferenceStatus.BOUND, 12L);
        PostMediaAsset pending = asset(uuid(606), uuid(306), PostMediaReferenceStatus.RELEASE_PENDING, 13L);
        when(fixture.mediaRepository.scanReferenceStatesAfter(ZERO_UUID, 2))
                .thenReturn(List.of(failed, pending));
        when(fixture.postRepository.getRequiredSnapshot(failed.postId()))
                .thenReturn(new PostSnapshot(failed.postId(), failed.ownerUserId(), 0, Date.from(NOW)));
        when(fixture.queryPort.findReferenceStatus(failed.ossObjectId(), failed.ossReferenceId()))
                .thenThrow(new IllegalStateException("OSS query unavailable"));

        PostMediaReferenceReconciliationResult result = fixture.service.reconcile(command(2));

        verify(fixture.publisher).publish(new PostMediaReferenceCommand(
                pending.id(),
                PostMediaReferenceOperation.RELEASE,
                13L,
                pending.ownerUserId()
        ));
        assertThat(result).isEqualTo(new PostMediaReferenceReconciliationResult(
                pending.id(), true, 2, 1, 0, 1, 1
        ));
        verify(fixture.metrics).recordReconciliation("remote_query", "failed");
    }

    private ReconcilePostMediaReferencesCommand command(int batchSize) {
        return new ReconcilePostMediaReferencesCommand(ZERO_UUID, batchSize);
    }

    private Fixture fixture() {
        PostMediaAssetRepository mediaRepository = mock(PostMediaAssetRepository.class);
        PostRepository postRepository = mock(PostRepository.class);
        PostMediaReferenceQueryPort queryPort = mock(PostMediaReferenceQueryPort.class);
        PostMediaReferenceCommandPublisher publisher = mock(PostMediaReferenceCommandPublisher.class);
        PostMediaReferenceMetrics metrics = mock(PostMediaReferenceMetrics.class);
        return new Fixture(
                mediaRepository,
                postRepository,
                queryPort,
                publisher,
                metrics,
                new PostMediaReferenceReconciliationApplicationService(
                        mediaRepository,
                        postRepository,
                        queryPort,
                        publisher,
                        metrics,
                        CLOCK
                )
        );
    }

    private PostMediaAsset asset(
            UUID assetId,
            UUID postId,
            PostMediaReferenceStatus status,
            long version
    ) {
        PostMediaAssetLifecycle lifecycle = switch (status) {
            case UNBOUND, BIND_PENDING -> PostMediaAssetLifecycle.UPLOADED;
            case RELEASED -> PostMediaAssetLifecycle.RELEASED;
            default -> PostMediaAssetLifecycle.BOUND;
        };
        return new PostMediaAsset(
                assetId,
                uuid(7),
                postId,
                uuid(701 + Math.toIntExact(version)),
                uuid(801 + Math.toIntExact(version)),
                uuid(901 + Math.toIntExact(version)),
                null,
                "asset.bin",
                "application/octet-stream",
                512L,
                PostMediaKind.FILE,
                lifecycle,
                status,
                version,
                Date.from(NOW),
                PostVideoState.NONE,
                "https://cdn.example.com/asset.bin",
                "",
                Date.from(NOW),
                Date.from(NOW)
        );
    }

    private record Fixture(
            PostMediaAssetRepository mediaRepository,
            PostRepository postRepository,
            PostMediaReferenceQueryPort queryPort,
            PostMediaReferenceCommandPublisher publisher,
            PostMediaReferenceMetrics metrics,
            PostMediaReferenceReconciliationApplicationService service
    ) {
    }
}
