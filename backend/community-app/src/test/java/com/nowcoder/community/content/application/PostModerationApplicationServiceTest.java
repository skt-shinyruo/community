package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.service.PostModerationDomainService;
import com.nowcoder.community.user.api.action.UserModerationActionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PostModerationApplicationServiceTest {

    private static final long POST_VERSION = 7L;
    private static final Instant NOW = Instant.parse("2025-01-02T03:04:05Z");
    private static final Date NOW_DATE = Date.from(NOW);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private PostModerationDomainService domainService;
    private PostRepository postRepository;
    private PostIntegrationEventPublisher integrationEventPublisher;
    private PostMediaReferenceScheduler mediaReferenceScheduler;
    private UserModerationActionApi userModerationActionApi;
    private PostModerationApplicationService service;

    @BeforeEach
    void setUp() {
        domainService = mock(PostModerationDomainService.class);
        postRepository = mock(PostRepository.class);
        integrationEventPublisher = mock(PostIntegrationEventPublisher.class);
        mediaReferenceScheduler = mock(PostMediaReferenceScheduler.class);
        userModerationActionApi = mock(UserModerationActionApi.class);
        service = new PostModerationApplicationService(
                domainService,
                postRepository,
                integrationEventPublisher,
                mediaReferenceScheduler,
                new PostBusinessEventLogger(),
                userModerationActionApi,
                CLOCK
        );
    }

    @Test
    void topWonderfulAndDeleteShouldOwnPostModerationOrchestration(CapturedOutput output) {
        UUID actorUserId = uuid(9);
        UUID postId = uuid(101);
        PostSnapshot post = postSnapshot(postId, 0);
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(domainService.shouldAdminDelete(actorUserId, post)).thenReturn(true);
        when(postRepository.markDeletedByAdmin(
                eq(postId), eq(actorUserId), eq(NOW_DATE), eq(POST_VERSION)
        )).thenReturn(true);

        service.top(actorUserId, postId);
        service.wonderful(actorUserId, postId);
        service.delete(actorUserId, postId);

        InOrder inOrder = inOrder(
                domainService,
                postRepository,
                integrationEventPublisher,
                mediaReferenceScheduler
        );
        inOrder.verify(postRepository).getRequiredSnapshot(postId);
        inOrder.verify(domainService).assertCanModeratePost(actorUserId, post);
        inOrder.verify(postRepository).markTop(postId, NOW_DATE, POST_VERSION);
        inOrder.verify(integrationEventPublisher).postUpdated(postId);
        inOrder.verify(postRepository).getRequiredSnapshot(postId);
        inOrder.verify(domainService).assertCanModeratePost(actorUserId, post);
        inOrder.verify(postRepository).markWonderful(postId, NOW_DATE, POST_VERSION);
        inOrder.verify(integrationEventPublisher).postUpdated(postId);
        inOrder.verify(postRepository).getRequiredSnapshot(postId);
        inOrder.verify(domainService).shouldAdminDelete(actorUserId, post);
        inOrder.verify(postRepository).markDeletedByAdmin(
                postId, actorUserId, NOW_DATE, POST_VERSION
        );
        inOrder.verify(mediaReferenceScheduler).scheduleReleaseForDeletedPost(postId);
        inOrder.verify(integrationEventPublisher).postDeleted(postId);

        assertThat(output.getAll())
                .contains("community.reason_code=admin_delete")
                .contains("community.target_type=post")
                .contains("community.target_id=" + postId)
                .contains("user.id=" + actorUserId);
    }

    @Test
    void deleteShouldReturnWithoutSideEffectsWhenDomainDeclinesAdminDelete(CapturedOutput output) {
        UUID actorUserId = uuid(9);
        UUID postId = uuid(101);
        PostSnapshot post = postSnapshot(postId, 2);
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(domainService.shouldAdminDelete(actorUserId, post)).thenReturn(false);

        service.delete(actorUserId, postId);

        InOrder inOrder = inOrder(domainService, postRepository);
        inOrder.verify(postRepository).getRequiredSnapshot(postId);
        inOrder.verify(domainService).shouldAdminDelete(actorUserId, post);
        verifyNoMoreInteractions(integrationEventPublisher, mediaReferenceScheduler);
        assertThat(output.getAll()).doesNotContain("community.reason_code=admin_delete");
    }

    @Test
    void deleteShouldSkipSideEffectsWhenDeleteDidNotChangePostState(CapturedOutput output) {
        UUID actorUserId = uuid(9);
        UUID postId = uuid(101);
        PostSnapshot post = postSnapshot(postId, 0);
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(domainService.shouldAdminDelete(actorUserId, post)).thenReturn(true);
        when(postRepository.markDeletedByAdmin(
                eq(postId), eq(actorUserId), eq(NOW_DATE), eq(POST_VERSION)
        )).thenReturn(false);

        service.delete(actorUserId, postId);

        verify(integrationEventPublisher, never()).postDeleted(any(UUID.class));
        verify(mediaReferenceScheduler, never()).scheduleReleaseForDeletedPost(any(UUID.class));
        assertThat(output.getAll()).doesNotContain("community.reason_code=admin_delete");
    }

    @Test
    void deleteByModerationShouldSkipSideEffectsWhenDeleteDidNotChangePostState(CapturedOutput output) {
        UUID actorUserId = uuid(9);
        UUID postId = uuid(101);
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(postSnapshot(postId, 0));
        when(postRepository.markDeletedByAdmin(
                eq(postId), eq(actorUserId), eq(NOW_DATE), eq(POST_VERSION)
        )).thenReturn(false);

        service.deleteByModeration(actorUserId, postId);

        verify(integrationEventPublisher, never()).postDeleted(any(UUID.class));
        verify(mediaReferenceScheduler, never()).scheduleReleaseForDeletedPost(any(UUID.class));
        assertThat(output.getAll()).doesNotContain("community.reason_code=admin_delete");
    }

    @Test
    void deleteByModerationShouldPublishDeletionOnlyForTheStateChangingCall() {
        UUID actorUserId = uuid(9);
        UUID postId = uuid(101);
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(postSnapshot(postId, 0));
        when(postRepository.markDeletedByAdmin(
                eq(postId), eq(actorUserId), eq(NOW_DATE), eq(POST_VERSION)
        ))
                .thenReturn(true, false);

        service.deleteByModeration(actorUserId, postId);
        service.deleteByModeration(actorUserId, postId);

        verify(integrationEventPublisher, times(1)).postDeleted(postId);
        verify(mediaReferenceScheduler, times(1)).scheduleReleaseForDeletedPost(postId);
    }

    @Test
    void postModerationApplicationServiceShouldNotDependOnPostWriteSideEffectScheduler() {
        assertThat(java.util.Arrays.stream(PostModerationApplicationService.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .doesNotContain("com.nowcoder.community.content.application.PostWriteSideEffectScheduler");
    }

    private PostSnapshot postSnapshot(UUID postId, int status) {
        Date timestamp = new Date();
        return new PostSnapshot(postId, uuid(7), 0, status, timestamp, timestamp, POST_VERSION);
    }
}
