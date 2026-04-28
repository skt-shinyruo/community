package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.service.PostModerationDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PostModerationApplicationServiceTest {

    private PostModerationDomainService domainService;
    private PostRepository postRepository;
    private PostDomainEventPublisher domainEventPublisher;
    private PostWriteSideEffectScheduler postWriteSideEffectScheduler;
    private PostModerationApplicationService service;

    @BeforeEach
    void setUp() {
        domainService = mock(PostModerationDomainService.class);
        postRepository = mock(PostRepository.class);
        domainEventPublisher = mock(PostDomainEventPublisher.class);
        postWriteSideEffectScheduler = mock(PostWriteSideEffectScheduler.class);
        service = new PostModerationApplicationService(
                domainService,
                postRepository,
                domainEventPublisher,
                postWriteSideEffectScheduler,
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

        service.top(actorUserId, postId);
        service.wonderful(actorUserId, postId);
        service.delete(actorUserId, postId);

        InOrder inOrder = inOrder(domainService, postRepository, domainEventPublisher, postWriteSideEffectScheduler);
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
}
