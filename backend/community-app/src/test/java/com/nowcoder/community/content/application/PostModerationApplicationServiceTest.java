package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.service.PostModerationDomainService;
import com.nowcoder.community.social.api.action.SocialLikeCleanupActionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PostModerationApplicationServiceTest {

    private PostModerationDomainService domainService;
    private PostRepository postRepository;
    private PostDomainEventPublisher domainEventPublisher;
    private PostWriteSideEffectScheduler postWriteSideEffectScheduler;
    private SocialLikeCleanupActionApi socialLikeCleanupActionApi;
    private PostModerationApplicationService service;

    @BeforeEach
    void setUp() {
        domainService = mock(PostModerationDomainService.class);
        postRepository = mock(PostRepository.class);
        domainEventPublisher = mock(PostDomainEventPublisher.class);
        postWriteSideEffectScheduler = mock(PostWriteSideEffectScheduler.class);
        socialLikeCleanupActionApi = mock(SocialLikeCleanupActionApi.class);
        service = new PostModerationApplicationService(
                domainService,
                postRepository,
                domainEventPublisher,
                postWriteSideEffectScheduler,
                socialLikeCleanupActionApi,
                new PostBusinessEventLogger()
        );
    }

    @Test
    void topWonderfulAndDeleteShouldOwnPostModerationOrchestration(CapturedOutput output) {
        UUID actorUserId = uuid(9);
        UUID postId = uuid(101);
        PostSnapshot post = new PostSnapshot(postId, uuid(7), 0, new Date());
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(domainService.shouldAdminDelete(actorUserId, post)).thenReturn(true);
        when(postRepository.markDeletedByAdmin(eq(postId), eq(actorUserId), any(Date.class))).thenReturn(true);

        service.top(actorUserId, postId);
        service.wonderful(actorUserId, postId);
        service.delete(actorUserId, postId);

        InOrder inOrder = inOrder(domainService, postRepository, domainEventPublisher, postWriteSideEffectScheduler, socialLikeCleanupActionApi);
        inOrder.verify(postRepository).getRequiredSnapshot(postId);
        inOrder.verify(domainService).assertCanModeratePost(actorUserId, post);
        inOrder.verify(postRepository).markTop(postId);
        inOrder.verify(domainEventPublisher).postUpdated(postId);
        inOrder.verify(postRepository).getRequiredSnapshot(postId);
        inOrder.verify(domainService).assertCanModeratePost(actorUserId, post);
        inOrder.verify(postRepository).markWonderful(postId);
        inOrder.verify(domainEventPublisher).postUpdated(postId);
        inOrder.verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(postId);
        inOrder.verify(postRepository).getRequiredSnapshot(postId);
        inOrder.verify(domainService).shouldAdminDelete(actorUserId, post);
        inOrder.verify(postRepository).markDeletedByAdmin(eq(postId), eq(actorUserId), any(Date.class));
        inOrder.verify(domainEventPublisher).postDeleted(postId);
        inOrder.verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.POST, postId);
        inOrder.verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(postId);

        assertThat(output.getAll())
                .contains("community.action=post_top")
                .contains("community.action=post_wonderful")
                .contains("community.action=post_delete")
                .contains("community.reason_code=admin_delete")
                .contains("community.target_type=post")
                .contains("community.target_id=" + postId)
                .contains("user.id=" + actorUserId);
    }

    @Test
    void deleteShouldReturnWithoutSideEffectsWhenDomainDeclinesAdminDelete(CapturedOutput output) {
        UUID actorUserId = uuid(9);
        UUID postId = uuid(101);
        PostSnapshot post = new PostSnapshot(postId, uuid(7), 2, new Date());
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(domainService.shouldAdminDelete(actorUserId, post)).thenReturn(false);

        service.delete(actorUserId, postId);

        InOrder inOrder = inOrder(domainService, postRepository);
        inOrder.verify(postRepository).getRequiredSnapshot(postId);
        inOrder.verify(domainService).shouldAdminDelete(actorUserId, post);
        verifyNoMoreInteractions(domainEventPublisher, postWriteSideEffectScheduler);
        assertThat(output.getAll()).doesNotContain("community.action=post_delete");
    }

    @Test
    void deleteShouldSkipSideEffectsWhenDeleteDidNotChangePostState(CapturedOutput output) {
        UUID actorUserId = uuid(9);
        UUID postId = uuid(101);
        PostSnapshot post = new PostSnapshot(postId, uuid(7), 0, new Date());
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(domainService.shouldAdminDelete(actorUserId, post)).thenReturn(true);
        when(postRepository.markDeletedByAdmin(eq(postId), eq(actorUserId), any(Date.class))).thenReturn(false);

        service.delete(actorUserId, postId);

        verify(domainEventPublisher, never()).postDeleted(any(UUID.class));
        verify(socialLikeCleanupActionApi, never()).cleanupEntityLikes(any(Integer.class), any(UUID.class));
        verify(postWriteSideEffectScheduler, never()).schedulePostScoreRefresh(any(UUID.class));
        assertThat(output.getAll()).doesNotContain("community.action=post_delete");
    }

    @Test
    void deleteByModerationShouldSkipSideEffectsWhenDeleteDidNotChangePostState(CapturedOutput output) {
        UUID actorUserId = uuid(9);
        UUID postId = uuid(101);
        when(postRepository.markDeletedByAdmin(eq(postId), eq(actorUserId), any(Date.class))).thenReturn(false);

        service.deleteByModeration(actorUserId, postId);

        verify(domainEventPublisher, never()).postDeleted(any(UUID.class));
        verify(socialLikeCleanupActionApi, never()).cleanupEntityLikes(any(Integer.class), any(UUID.class));
        verify(postWriteSideEffectScheduler, never()).schedulePostScoreRefresh(any(UUID.class));
        assertThat(output.getAll()).doesNotContain("community.action=post_delete");
    }

    @Test
    void deleteShouldRunSocialCleanupAfterCommitWhenTransactionActive() {
        UUID actorUserId = uuid(9);
        UUID postId = uuid(101);
        PostSnapshot post = new PostSnapshot(postId, uuid(7), 0, new Date());
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(domainService.shouldAdminDelete(actorUserId, post)).thenReturn(true);
        when(postRepository.markDeletedByAdmin(eq(postId), eq(actorUserId), any(Date.class))).thenReturn(true);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.delete(actorUserId, postId);

            verify(socialLikeCleanupActionApi, never()).cleanupEntityLikes(any(Integer.class), any(UUID.class));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.POST, postId);
    }
}
