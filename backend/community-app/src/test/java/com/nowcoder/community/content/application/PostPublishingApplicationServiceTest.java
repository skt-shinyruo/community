package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.content.application.command.CreatePostCommand;
import com.nowcoder.community.content.application.result.PostCreateResult;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.CategoryRepository;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.repository.PostTagRepository;
import com.nowcoder.community.content.domain.service.PostPublishingDomainService;
import com.nowcoder.community.content.application.ContentTextCodec;
import com.nowcoder.community.content.application.ContentSanitizer;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.social.api.action.SocialLikeCleanupActionApi;
import com.nowcoder.community.user.api.action.UserPointsAwardActionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PostPublishingApplicationServiceTest {

    private ContentSanitizer sensitiveFilter;
    private IdempotencyGuard idempotencyGuard;
    private UserModerationGuard moderationGuard;
    private PostPublishingDomainService domainService;
    private PostRepository postRepository;
    private CategoryRepository categoryRepository;
    private PostTagRepository postTagRepository;
    private PostDomainEventPublisher domainEventPublisher;
    private PostWriteSideEffectScheduler postWriteSideEffectScheduler;
    private SocialLikeCleanupActionApi socialLikeCleanupActionApi;
    private UserPointsAwardActionApi pointsAwardService;
    private GrowthTaskProgressActionApi taskProgressTriggerService;
    private PostPublishingApplicationService service;

    @BeforeEach
    void setUp() {
        sensitiveFilter = mock(ContentSanitizer.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        moderationGuard = mock(UserModerationGuard.class);
        domainService = mock(PostPublishingDomainService.class);
        postRepository = mock(PostRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        postTagRepository = mock(PostTagRepository.class);
        domainEventPublisher = mock(PostDomainEventPublisher.class);
        postWriteSideEffectScheduler = mock(PostWriteSideEffectScheduler.class);
        socialLikeCleanupActionApi = mock(SocialLikeCleanupActionApi.class);
        pointsAwardService = mock(UserPointsAwardActionApi.class);
        taskProgressTriggerService = mock(GrowthTaskProgressActionApi.class);
        service = new PostPublishingApplicationService(
                sensitiveFilter,
                idempotencyGuard,
                new ContentTextCodec(new ContentRenderProperties()),
                new PostBusinessEventLogger(),
                moderationGuard,
                domainService,
                postRepository,
                categoryRepository,
                postTagRepository,
                domainEventPublisher,
                postWriteSideEffectScheduler,
                socialLikeCleanupActionApi,
                pointsAwardService,
                taskProgressTriggerService
        );
    }

    @Test
    void createShouldOwnPublishingOrchestrationInsideApplicationLayer(CapturedOutput output) {
        UUID userId = uuid(7);
        UUID categoryId = uuid(1);
        UUID postId = uuid(99);
        Date createTime = Date.from(Instant.parse("2026-04-27T08:00:00Z"));
        PostDraft draft = new PostDraft(userId, "title", "content", categoryId, createTime);

        when(sensitiveFilter.filter("<title>")).thenReturn("title");
        when(sensitiveFilter.filter("<content>")).thenReturn("content");
        when(idempotencyGuard.executeRequired(eq("content:create_post"), eq(userId), anyString(), eq(PostCreateResult.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<PostCreateResult>>getArgument(4).get());
        when(domainService.createDraft(userId, "title", "content", categoryId)).thenReturn(draft);
        when(postRepository.create(draft)).thenReturn(postId);

        PostCreateResult response = service.create(
                "idem-1",
                new CreatePostCommand(userId, "<title>", "<content>", categoryId, List.of("java"))
        );

        assertThat(response.postId()).isEqualTo(postId);
        verify(idempotencyGuard).executeRequired(eq("content:create_post"), eq(userId), eq("idem-1"), eq(PostCreateResult.class), any());
        var inOrder = inOrder(
                moderationGuard,
                categoryRepository,
                domainService,
                postRepository,
                postTagRepository,
                pointsAwardService,
                taskProgressTriggerService,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );
        inOrder.verify(moderationGuard).assertCanSpeak(userId);
        inOrder.verify(categoryRepository).assertExists(categoryId);
        inOrder.verify(domainService).createDraft(userId, "title", "content", categoryId);
        inOrder.verify(postRepository).create(draft);
        inOrder.verify(postTagRepository).bindTagsToPost(postId, List.of("java"));
        inOrder.verify(pointsAwardService).awardPostPublished(postId, userId);
        inOrder.verify(taskProgressTriggerService).triggerPostPublished(postId, userId, createTime.toInstant());
        inOrder.verify(domainEventPublisher).postPublished(postId);
        inOrder.verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(postId);
        assertThat(output.getAll())
                .contains("community.action=post_create")
                .contains("community.post_category_id=" + categoryId)
                .contains("community.target_id=" + postId);
    }

    @Test
    void updateAndDeleteByAuthorShouldOwnAuthorWriteOrchestration(CapturedOutput output) {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        UUID categoryId = uuid(2);
        PostSnapshot post = new PostSnapshot(postId, userId, 0, Date.from(Instant.parse("2026-04-27T08:00:00Z")));

        when(sensitiveFilter.filter("<title>")).thenReturn("title");
        when(sensitiveFilter.filter("<content>")).thenReturn("content");
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(postRepository.markDeletedByAuthor(eq(postId), eq(userId), any(Date.class))).thenReturn(true);

        service.updatePost(userId, postId, "<title>", "<content>", categoryId, List.of("spring"));
        service.deleteByAuthor(userId, postId);

        verify(moderationGuard).assertCanSpeak(userId);
        verify(categoryRepository).assertExists(categoryId);
        verify(domainService).assertEditableByAuthor(eq(post), eq(userId), any(Date.class));
        verify(postRepository).updateContent(eq(postId), eq("title"), eq("content"), eq(categoryId), any(Date.class));
        verify(postTagRepository).replaceTagsForPost(postId, List.of("spring"));
        verify(domainEventPublisher).postUpdated(postId);
        verify(postWriteSideEffectScheduler, times(2)).schedulePostScoreRefresh(postId);
        verify(domainService).assertDeletableByAuthor(post, userId);
        verify(postRepository).markDeletedByAuthor(eq(postId), eq(userId), any(Date.class));
        verify(domainEventPublisher).postDeleted(postId);
        verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.POST, postId);
        assertThat(output.getAll())
                .contains("community.action=post_update")
                .contains("community.post_category_id=" + categoryId)
                .contains("community.action=post_delete")
                .contains("community.reason_code=author_delete")
                .contains("user.id=" + userId);
    }

    @Test
    void deleteByAuthorShouldSkipSideEffectsWhenDeleteDidNotChangePostState(CapturedOutput output) {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        PostSnapshot post = new PostSnapshot(postId, userId, 0, Date.from(Instant.parse("2026-04-27T08:00:00Z")));
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(postRepository.markDeletedByAuthor(eq(postId), eq(userId), any(Date.class))).thenReturn(false);

        service.deleteByAuthor(userId, postId);

        verify(domainService).assertDeletableByAuthor(post, userId);
        verify(domainEventPublisher, never()).postDeleted(postId);
        verify(socialLikeCleanupActionApi, never()).cleanupEntityLikes(any(Integer.class), any(UUID.class));
        verify(postWriteSideEffectScheduler, never()).schedulePostScoreRefresh(any(UUID.class));
        assertThat(output.getAll()).doesNotContain("community.action=post_delete");
    }
}
