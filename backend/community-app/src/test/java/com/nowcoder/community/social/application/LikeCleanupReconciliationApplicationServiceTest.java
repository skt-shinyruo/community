package com.nowcoder.community.social.application;

import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import com.nowcoder.community.social.application.command.ReconcileLikeCleanupCommand;
import com.nowcoder.community.social.application.result.LikeCleanupReconciliationResult;
import com.nowcoder.community.social.domain.model.LikeTargetState;
import com.nowcoder.community.social.domain.repository.LikeTargetStateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeCleanupReconciliationApplicationServiceTest {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    @Test
    void reconciliationBatchMustNotOwnATransactionAcrossAllTargets() throws Exception {
        assertThat(LikeCleanupReconciliationApplicationService.class.isAnnotationPresent(Transactional.class))
                .isFalse();
        assertThat(LikeCleanupReconciliationApplicationService.class
                .getDeclaredMethod("reconcile", ReconcileLikeCleanupCommand.class)
                .isAnnotationPresent(Transactional.class))
                .isFalse();
    }

    @Test
    void reconcileShouldScanSocialDeletionFactsWithoutAdvancingTheirOwnerVersion() {
        LikeTargetStateRepository targetStateRepository = mock(LikeTargetStateRepository.class);
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        LikeTargetState first = deleted(uuid(701), 11L);
        LikeTargetState second = deleted(uuid(702), 12L);
        when(targetStateRepository.scanDeletedTargetsWithLikesAfter(POST, ZERO_UUID, 2))
                .thenReturn(List.of(first, second));
        LikeCleanupReconciliationApplicationService service = newService(
                targetStateRepository,
                likeApplicationService
        );

        LikeCleanupReconciliationResult result = service.reconcile(
                new ReconcileLikeCleanupCommand(POST, ZERO_UUID, 2)
        );

        assertThat(result.nextAfterEntityId()).isEqualTo(second.entityId());
        assertThat(result.hasMore()).isTrue();
        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.orphanTargets()).isEqualTo(2);
        assertThat(result.cleaned()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        ArgumentCaptor<CleanupDeletedContentLikesCommand> commands =
                ArgumentCaptor.forClass(CleanupDeletedContentLikesCommand.class);
        verify(likeApplicationService, times(2)).cleanupDeletedContentLikes(commands.capture());
        assertThat(commands.getAllValues())
                .containsExactly(
                        cleanupCommand(first),
                        cleanupCommand(second)
                );
    }

    @Test
    void reconcileShouldReturnAnEmptyPageWithoutInvokingCleanup() {
        LikeTargetStateRepository targetStateRepository = mock(LikeTargetStateRepository.class);
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        when(targetStateRepository.scanDeletedTargetsWithLikesAfter(POST, ZERO_UUID, 10))
                .thenReturn(List.of());
        LikeCleanupReconciliationApplicationService service = newService(
                targetStateRepository,
                likeApplicationService
        );

        LikeCleanupReconciliationResult result = service.reconcile(
                new ReconcileLikeCleanupCommand(POST, ZERO_UUID, 10)
        );

        assertThat(result.nextAfterEntityId()).isEqualTo(ZERO_UUID);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.scanned()).isZero();
        assertThat(result.orphanTargets()).isZero();
        assertThat(result.cleaned()).isZero();
        assertThat(result.failed()).isZero();
        verify(likeApplicationService, never()).cleanupDeletedContentLikes(any());
    }

    @Test
    void cleanupFailureForOneDeletionFactShouldNotBlockLaterTargetsOrCursorProgress() {
        LikeTargetStateRepository targetStateRepository = mock(LikeTargetStateRepository.class);
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        LikeTargetState first = deleted(uuid(705), 15L);
        LikeTargetState second = deleted(uuid(706), 16L);
        when(targetStateRepository.scanDeletedTargetsWithLikesAfter(POST, ZERO_UUID, 2))
                .thenReturn(List.of(first, second));
        when(likeApplicationService.cleanupDeletedContentLikes(any()))
                .thenThrow(new IllegalStateException("first cleanup failed"))
                .thenReturn(2L);
        LikeCleanupReconciliationApplicationService service = newService(
                targetStateRepository,
                likeApplicationService
        );

        LikeCleanupReconciliationResult result = service.reconcile(
                new ReconcileLikeCleanupCommand(POST, ZERO_UUID, 2)
        );

        assertThat(result.nextAfterEntityId()).isEqualTo(second.entityId());
        assertThat(result.hasMore()).isTrue();
        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.orphanTargets()).isEqualTo(2);
        assertThat(result.cleaned()).isOne();
        assertThat(result.failed()).isOne();
        verify(likeApplicationService, times(2)).cleanupDeletedContentLikes(any());
    }

    private LikeCleanupReconciliationApplicationService newService(
            LikeTargetStateRepository targetStateRepository,
            LikeApplicationService likeApplicationService
    ) {
        return new LikeCleanupReconciliationApplicationService(
                targetStateRepository,
                likeApplicationService,
                mock(LikeCleanupMetrics.class)
        );
    }

    private LikeTargetState deleted(UUID entityId, long sourceVersion) {
        return LikeTargetState.active(POST, entityId).applyDeletion(
                "content:PostDeleted:" + entityId,
                sourceVersion,
                Instant.parse("2026-07-15T08:30:00Z").plusSeconds(sourceVersion)
        );
    }

    private CleanupDeletedContentLikesCommand cleanupCommand(LikeTargetState state) {
        return new CleanupDeletedContentLikesCommand(
                state.entityType(),
                state.entityId(),
                "social-like-reconciliation:"
                        + state.entityType()
                        + ":"
                        + state.entityId()
                        + ":"
                        + state.sourceVersion(),
                state.sourceVersion(),
                state.deletedAt()
        );
    }
}
