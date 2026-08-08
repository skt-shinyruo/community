package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentThreadCleanupApplicationServiceTest {

    @Test
    void reconcileShouldDrainEachRootInBoundedBatchesAndIsolateFailures() {
        UUID firstRootId = uuid(9101);
        UUID failedRootId = uuid(9102);
        UUID postId = uuid(9103);
        UUID moderatorId = uuid(9104);
        Date deletedAt = new Date(2_000_000L);
        CommentRepository repository = mock(CommentRepository.class);
        CommentDeletionTransactionOperations operations =
                mock(CommentDeletionTransactionOperations.class);
        CommentThreadCleanupApplicationService service =
                new CommentThreadCleanupApplicationService(repository, operations);
        CommentSnapshot firstRoot =
                deletedRoot(firstRootId, postId, moderatorId, deletedAt);
        CommentSnapshot failedRoot =
                deletedRoot(failedRootId, postId, moderatorId, deletedAt);
        CommentSnapshot reply = activeReply(uuid(9105), firstRootId, postId);

        when(repository.findDeletedRootIdsWithActiveReplies(2))
                .thenReturn(List.of(firstRootId, failedRootId));
        when(repository.findSnapshot(firstRootId)).thenReturn(Optional.of(firstRoot));
        when(repository.findSnapshot(failedRootId)).thenReturn(Optional.of(failedRoot));
        when(operations.deleteReplyBatch(
                firstRootId,
                postId,
                moderatorId,
                "hide: spam",
                deletedAt,
                CommentDeletionTransactionOperations.REPLY_BATCH_SIZE
        )).thenReturn(
                CommentDeletionResult.applied(List.of(reply)),
                CommentDeletionResult.noOp()
        );
        when(operations.deleteReplyBatch(
                failedRootId,
                postId,
                moderatorId,
                "hide: spam",
                deletedAt,
                CommentDeletionTransactionOperations.REPLY_BATCH_SIZE
        )).thenThrow(new IllegalStateException("row lock timeout"));

        CommentThreadCleanupApplicationService.CleanupResult result =
                service.reconcile(2);

        assertThat(result).isEqualTo(
                new CommentThreadCleanupApplicationService.CleanupResult(2, 1, 1));
        verify(operations, times(2)).deleteReplyBatch(
                firstRootId,
                postId,
                moderatorId,
                "hide: spam",
                deletedAt,
                CommentDeletionTransactionOperations.REPLY_BATCH_SIZE
        );
        verify(operations).deleteReplyBatch(
                failedRootId,
                postId,
                moderatorId,
                "hide: spam",
                deletedAt,
                CommentDeletionTransactionOperations.REPLY_BATCH_SIZE
        );
    }

    @Test
    void reconcileShouldClampTheScanBatch() {
        CommentRepository repository = mock(CommentRepository.class);
        CommentThreadCleanupApplicationService service =
                new CommentThreadCleanupApplicationService(
                        repository, mock(CommentDeletionTransactionOperations.class));
        when(repository.findDeletedRootIdsWithActiveReplies(500)).thenReturn(List.of());

        assertThat(service.reconcile(5_000).scanned()).isZero();

        verify(repository).findDeletedRootIdsWithActiveReplies(500);
    }

    private static CommentSnapshot deletedRoot(
            UUID id,
            UUID postId,
            UUID deletedBy,
            Date deletedAt
    ) {
        return new CommentSnapshot(
                id,
                uuid(9190),
                postId,
                id,
                null,
                null,
                "deleted root",
                1,
                new Date(1_000_000L),
                deletedAt,
                0,
                deletedBy,
                "hide: spam",
                deletedAt,
                8L
        );
    }

    private static CommentSnapshot activeReply(UUID id, UUID rootId, UUID postId) {
        return new CommentSnapshot(
                id,
                uuid(9191),
                postId,
                rootId,
                rootId,
                uuid(9190),
                "reply",
                0,
                new Date(1_100_000L),
                null,
                0,
                null,
                null,
                null,
                2L
        );
    }
}
