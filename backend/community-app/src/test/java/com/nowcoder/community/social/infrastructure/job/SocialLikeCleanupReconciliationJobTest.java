package com.nowcoder.community.social.infrastructure.job;

import com.nowcoder.community.social.application.LikeCleanupReconciliationApplicationService;
import com.nowcoder.community.social.application.command.ReconcileLikeCleanupCommand;
import com.nowcoder.community.social.application.result.LikeCleanupReconciliationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialLikeCleanupReconciliationJobTest {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    @Test
    void disabledJobShouldNotEnterApplicationBoundary() {
        LikeCleanupReconciliationApplicationService service =
                mock(LikeCleanupReconciliationApplicationService.class);
        SocialLikeCleanupReconciliationJob job =
                new SocialLikeCleanupReconciliationJob(service, false, 50);

        job.reconcile();

        verify(service, never()).reconcile(any());
    }

    @Test
    void enabledJobShouldDelegatePostAndCommentBatchesAndAdvanceReturnedCursors() {
        LikeCleanupReconciliationApplicationService service =
                mock(LikeCleanupReconciliationApplicationService.class);
        when(service.reconcile(any())).thenAnswer(invocation -> {
            ReconcileLikeCleanupCommand command = invocation.getArgument(0);
            UUID next = command.entityType() == POST ? uuid(801) : uuid(802);
            return new LikeCleanupReconciliationResult(next, true, 1, 0, 0, 0);
        });
        SocialLikeCleanupReconciliationJob job =
                new SocialLikeCleanupReconciliationJob(service, true, 50);

        job.reconcile();
        job.reconcile();

        ArgumentCaptor<ReconcileLikeCleanupCommand> commands =
                ArgumentCaptor.forClass(ReconcileLikeCleanupCommand.class);
        verify(service, times(4)).reconcile(commands.capture());
        assertThat(commands.getAllValues())
                .extracting(ReconcileLikeCleanupCommand::entityType)
                .containsExactly(POST, COMMENT, POST, COMMENT);
        assertThat(commands.getAllValues())
                .extracting(ReconcileLikeCleanupCommand::afterEntityId)
                .containsExactly(ZERO_UUID, ZERO_UUID, uuid(801), uuid(802));
        assertThat(commands.getAllValues())
                .extracting(ReconcileLikeCleanupCommand::batchSize)
                .containsOnly(50);
    }

    @Test
    void jobMustNotHoldForeignApiRepositoryMapperOrPersistenceCollaborators() {
        List<String> forbiddenFields = List.of(SocialLikeCleanupReconciliationJob.class.getDeclaredFields()).stream()
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .map(Class::getName)
                .filter(this::isForbiddenJobDependency)
                .toList();

        assertThat(forbiddenFields)
                .as("an inbound job may hold only its same-domain ApplicationService and scalar scheduling state")
                .isEmpty();
    }

    private boolean isForbiddenJobDependency(String typeName) {
        return typeName.startsWith("com.nowcoder.community.content.api.")
                || typeName.startsWith("com.nowcoder.community.social.domain.repository.")
                || typeName.startsWith("com.nowcoder.community.social.infrastructure.persistence.")
                || typeName.contains(".mapper.")
                || typeName.endsWith("Mapper");
    }
}
