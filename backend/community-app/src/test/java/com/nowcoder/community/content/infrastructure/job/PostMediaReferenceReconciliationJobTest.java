package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.content.application.PostMediaReferenceReconciliationApplicationService;
import com.nowcoder.community.content.application.command.ReconcilePostMediaReferencesCommand;
import com.nowcoder.community.content.application.result.PostMediaReferenceReconciliationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostMediaReferenceReconciliationJobTest {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    @Test
    void disabledWorkerShouldLeavePendingRowsUntouchedByNotEnteringTheUseCase() {
        PostMediaReferenceReconciliationApplicationService applicationService =
                mock(PostMediaReferenceReconciliationApplicationService.class);
        PostMediaReferenceReconciliationJob job =
                new PostMediaReferenceReconciliationJob(applicationService, false, 50);

        job.reconcile();

        verify(applicationService, never()).reconcile(any());
    }

    @Test
    void enabledWorkerShouldDelegateBoundedBatchesAndAdvanceApplicationCursor() {
        PostMediaReferenceReconciliationApplicationService applicationService =
                mock(PostMediaReferenceReconciliationApplicationService.class);
        UUID next = uuid(701);
        when(applicationService.reconcile(any()))
                .thenReturn(
                        new PostMediaReferenceReconciliationResult(next, true, 1, 0, 0, 0, 0),
                        new PostMediaReferenceReconciliationResult(next, false, 1, 0, 0, 0, 0)
                );
        PostMediaReferenceReconciliationJob job =
                new PostMediaReferenceReconciliationJob(applicationService, true, 50);

        job.reconcile();
        job.reconcile();
        job.reconcile();

        ArgumentCaptor<ReconcilePostMediaReferencesCommand> commands =
                ArgumentCaptor.forClass(ReconcilePostMediaReferencesCommand.class);
        verify(applicationService, times(3)).reconcile(commands.capture());
        assertThat(commands.getAllValues())
                .extracting(ReconcilePostMediaReferencesCommand::afterAssetId)
                .containsExactly(ZERO_UUID, next, ZERO_UUID);
        assertThat(commands.getAllValues())
                .extracting(ReconcilePostMediaReferencesCommand::batchSize)
                .containsOnly(50);
    }

    @Test
    void inboundJobShouldHoldOnlySameDomainApplicationServiceAndScalarSchedulingState() {
        List<String> forbiddenFields = Arrays.stream(PostMediaReferenceReconciliationJob.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .map(Class::getName)
                .filter(this::isForbiddenDependency)
                .toList();

        assertThat(forbiddenFields).isEmpty();
    }

    private boolean isForbiddenDependency(String typeName) {
        return typeName.equals("com.nowcoder.community.content.application.PostMediaReferenceQueryPort")
                || typeName.startsWith("com.nowcoder.community.content.domain.")
                || typeName.startsWith("com.nowcoder.community.content.infrastructure.persistence.")
                || typeName.equals("com.nowcoder.community.content.application.PostMediaStoragePort")
                || typeName.contains(".mapper.");
    }
}
