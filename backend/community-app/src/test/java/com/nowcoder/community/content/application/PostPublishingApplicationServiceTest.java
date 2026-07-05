package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.content.application.command.CreatePostCommand;
import com.nowcoder.community.content.application.command.PostContentBlockCommand;
import com.nowcoder.community.content.application.PostMediaStoragePort;
import com.nowcoder.community.content.application.result.PostCreateResult;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.CategoryRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.repository.PostTagRepository;
import com.nowcoder.community.content.domain.service.PostContentBlockPolicy;
import com.nowcoder.community.content.domain.service.PostPublishingDomainService;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.content.application.ContentTextCodec;
import com.nowcoder.community.content.application.ContentSanitizer;
import com.nowcoder.community.content.infrastructure.text.SpringHtmlContentTextCodec;
import com.nowcoder.community.social.api.action.SocialLikeCleanupActionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(OutputCaptureExtension.class)
class PostPublishingApplicationServiceTest {

    private ContentSanitizer sensitiveFilter;
    private IdempotencyGuard idempotencyGuard;
    private UserModerationGuard moderationGuard;
    private PostPublishingDomainService domainService;
    private PostContentBlockPolicy blockPolicy;
    private PostRepository postRepository;
    private PostContentBlockRepository postContentBlockRepository;
    private PostMediaAssetRepository postMediaAssetRepository;
    private PostMediaStoragePort postMediaStoragePort;
    private CategoryRepository categoryRepository;
    private PostTagRepository postTagRepository;
    private PostDomainEventPublisher domainEventPublisher;
    private PostWriteSideEffectScheduler postWriteSideEffectScheduler;
    private SocialLikeCleanupActionApi socialLikeCleanupActionApi;
    private PostPublishingApplicationService service;

    @BeforeEach
    void setUp() {
        sensitiveFilter = mock(ContentSanitizer.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        moderationGuard = mock(UserModerationGuard.class);
        domainService = mock(PostPublishingDomainService.class);
        blockPolicy = mock(PostContentBlockPolicy.class);
        postRepository = mock(PostRepository.class);
        postContentBlockRepository = mock(PostContentBlockRepository.class);
        postMediaAssetRepository = mock(PostMediaAssetRepository.class);
        postMediaStoragePort = mock(PostMediaStoragePort.class);
        categoryRepository = mock(CategoryRepository.class);
        postTagRepository = mock(PostTagRepository.class);
        domainEventPublisher = mock(PostDomainEventPublisher.class);
        postWriteSideEffectScheduler = mock(PostWriteSideEffectScheduler.class);
        socialLikeCleanupActionApi = mock(SocialLikeCleanupActionApi.class);
        service = new PostPublishingApplicationService(
                sensitiveFilter,
                idempotencyGuard,
                new SpringHtmlContentTextCodec(),
                new PostBusinessEventLogger(),
                moderationGuard,
                domainService,
                blockPolicy,
                postRepository,
                postContentBlockRepository,
                postMediaAssetRepository,
                postMediaStoragePort,
                categoryRepository,
                postTagRepository,
                domainEventPublisher,
                postWriteSideEffectScheduler,
                socialLikeCleanupActionApi
        );
    }

    @Test
    void createShouldOwnPublishingOrchestrationInsideApplicationLayer(CapturedOutput output) {
        UUID userId = uuid(7);
        UUID categoryId = uuid(1);
        UUID postId = uuid(99);
        Date createTime = Date.from(Instant.parse("2026-04-27T08:00:00Z"));
        PostDraft draft = new PostDraft(userId, "title", categoryId, createTime);
        List<PostContentBlockCommand> blocks = List.of(new PostContentBlockCommand("paragraph", "<content>", null, null, "", "", null));
        List<PostContentBlockCommand> normalizedBlocks = List.of(new PostContentBlockCommand("paragraph", "<content>", null, "", "", "", null));

        when(sensitiveFilter.filter("&lt;title&gt;")).thenReturn("title");
        when(sensitiveFilter.filter("&lt;content&gt;")).thenReturn("content");
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(normalizedBlocks);
        when(idempotencyGuard.executeRequired(
                eq("content:create_post"),
                eq(userId),
                anyString(),
                anyString(),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(PostCreateResult.class),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<PostCreateResult>>getArgument(6).get());
        when(domainService.createDraft(userId, "title", categoryId)).thenReturn(draft);
        when(postRepository.create(draft)).thenReturn(postId);

        PostCreateResult response = service.create(
                "idem-1",
                new CreatePostCommand(userId, "<title>", categoryId, List.of("java"), blocks)
        );

        assertThat(response.postId()).isEqualTo(postId);
        verify(idempotencyGuard).executeRequired(
                eq("content:create_post"),
                eq(userId),
                eq("idem-1"),
                org.mockito.ArgumentMatchers.argThat(hash -> hash != null && !hash.isBlank()),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(PostCreateResult.class),
                any()
        );
        var inOrder = inOrder(
                moderationGuard,
                categoryRepository,
                domainService,
                postRepository,
                postContentBlockRepository,
                postTagRepository,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );
        inOrder.verify(moderationGuard).assertCanSpeak(userId);
        inOrder.verify(categoryRepository).assertExists(categoryId);
        inOrder.verify(domainService).createDraft(userId, "title", categoryId);
        inOrder.verify(postRepository).create(draft);
        inOrder.verify(postContentBlockRepository).replaceBlocks(eq(postId), any());
        inOrder.verify(postTagRepository).bindTagsToPost(postId, List.of("java"));
        inOrder.verify(domainEventPublisher).postPublished(postId);
        inOrder.verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(postId);
        assertThat(output.getAll())
                .contains("community.post_category_id=" + categoryId)
                .contains("community.target_id=" + postId);
    }

    @Test
    void updateAndDeleteByAuthorShouldOwnAuthorWriteOrchestration(CapturedOutput output) {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        UUID categoryId = uuid(2);
        PostSnapshot post = new PostSnapshot(postId, userId, 0, Date.from(Instant.parse("2026-04-27T08:00:00Z")));
        List<PostContentBlockCommand> blocks = List.of(new PostContentBlockCommand("paragraph", "<content>", null, null, "", "", null));
        List<PostContentBlockCommand> normalizedBlocks = List.of(new PostContentBlockCommand("paragraph", "<content>", null, "", "", "", null));

        when(sensitiveFilter.filter("&lt;title&gt;")).thenReturn("title");
        when(sensitiveFilter.filter("&lt;content&gt;")).thenReturn("content");
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(normalizedBlocks);
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(postRepository.markDeletedByAuthor(eq(postId), eq(userId), any(Date.class))).thenReturn(true);

        service.updatePost(userId, postId, "<title>", categoryId, List.of("spring"), blocks);
        service.deleteByAuthor(userId, postId);

        verify(moderationGuard).assertCanSpeak(userId);
        verify(categoryRepository).assertExists(categoryId);
        verify(domainService).assertEditableByAuthor(eq(post), eq(userId), any(Date.class));
        verify(postRepository).updatePostMeta(eq(postId), eq("title"), eq(categoryId), any(Date.class));
        verify(postContentBlockRepository).replaceBlocks(eq(postId), any());
        verify(postMediaAssetRepository).releaseRemovedFromPost(eq(postId), eq(List.of()), any(Date.class));
        verify(postTagRepository).replaceTagsForPost(postId, List.of("spring"));
        verify(domainEventPublisher).postUpdated(postId);
        verify(postWriteSideEffectScheduler, times(2)).schedulePostScoreRefresh(postId);
        verify(domainService).assertDeletableByAuthor(post, userId);
        verify(postRepository).markDeletedByAuthor(eq(postId), eq(userId), any(Date.class));
        verify(domainEventPublisher).postDeleted(postId);
        verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.POST, postId);
        assertThat(output.getAll())
                .contains("community.post_category_id=" + categoryId)
                .contains("community.reason_code=author_delete")
                .contains("user.id=" + userId);
    }

    @Test
    void updateShouldReleaseRemovedMediaStorageReferences() {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        UUID categoryId = uuid(2);
        UUID keptAssetId = uuid(201);
        UUID removedAssetId = uuid(202);
        PostSnapshot post = new PostSnapshot(postId, userId, 0, Date.from(Instant.parse("2026-04-27T08:00:00Z")));
        PostMediaAsset keptAsset = mediaAsset(keptAssetId, userId, postId, PostMediaKind.IMAGE);
        PostMediaAsset removedAsset = mediaAsset(removedAssetId, userId, postId, PostMediaKind.FILE);
        List<PostContentBlockCommand> blocks = List.of(new PostContentBlockCommand("image", "", keptAssetId, null, "caption", "", null));
        List<PostContentBlockCommand> normalizedBlocks = List.of(new PostContentBlockCommand("image", "", keptAssetId, "", "caption", "", null));

        when(sensitiveFilter.filter("&lt;title&gt;")).thenReturn("title");
        when(sensitiveFilter.filter("")).thenReturn("");
        when(sensitiveFilter.filter("caption")).thenReturn("caption");
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(normalizedBlocks);
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(postMediaAssetRepository.listByIds(List.of(keptAssetId))).thenReturn(List.of(keptAsset));
        when(postMediaAssetRepository.listByPostId(postId)).thenReturn(List.of(keptAsset, removedAsset));

        service.updatePost(userId, postId, "<title>", categoryId, List.of("spring"), blocks);

        verify(postMediaStoragePort).releaseReference(removedAsset, userId);
        verify(postMediaStoragePort, never()).releaseReference(keptAsset, userId);
        verify(postMediaAssetRepository).releaseRemovedFromPost(eq(postId), eq(List.of(keptAssetId)), any(Date.class));
    }

    @Test
    void createShouldReleaseOssReferenceWhenBindPersistenceFails() {
        UUID userId = uuid(7);
        UUID categoryId = uuid(1);
        UUID postId = uuid(99);
        UUID assetId = uuid(201);
        UUID referenceId = uuid(202);
        Date createTime = Date.from(Instant.parse("2026-04-27T08:00:00Z"));
        PostDraft draft = new PostDraft(userId, "title", categoryId, createTime);
        PostMediaAsset asset = uploadedAsset(assetId, userId);
        List<PostContentBlockCommand> blocks = List.of(new PostContentBlockCommand("image", "", assetId, null, "", "", null));
        List<PostContentBlockCommand> normalizedBlocks = List.of(new PostContentBlockCommand("image", "", assetId, "", "", "", null));

        when(sensitiveFilter.filter("title")).thenReturn("title");
        when(sensitiveFilter.filter("")).thenReturn("");
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(normalizedBlocks);
        when(idempotencyGuard.executeRequired(
                eq("content:create_post"),
                eq(userId),
                anyString(),
                anyString(),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(PostCreateResult.class),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<PostCreateResult>>getArgument(6).get());
        when(domainService.createDraft(userId, "title", categoryId)).thenReturn(draft);
        when(postRepository.create(draft)).thenReturn(postId);
        when(postMediaAssetRepository.listByIds(List.of(assetId))).thenReturn(List.of(asset));
        when(postMediaStoragePort.bindReference(asset, postId, userId)).thenReturn(referenceId);
        org.mockito.Mockito.doThrow(new RuntimeException("bind db failed"))
                .when(postMediaAssetRepository).bindToPost(eq(assetId), eq(postId), eq(referenceId), eq(PostVideoState.NONE), any(Date.class));

        Throwable thrown = catchThrowable(() -> service.create(
                "idem-bind-fail",
                new CreatePostCommand(userId, "title", categoryId, List.of(), blocks)
        ));

        assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessage("bind db failed");
        verify(postMediaStoragePort).releaseReference(org.mockito.ArgumentMatchers.argThat(released ->
                released != null
                        && assetId.equals(released.id())
                        && referenceId.equals(released.ossReferenceId())
                        && postId.equals(released.postId())
        ), eq(userId));
    }

    @Test
    void updateShouldReleaseStorageReferencesAfterRepositoryStateChanges() {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        UUID categoryId = uuid(2);
        UUID removedAssetId = uuid(202);
        PostSnapshot post = new PostSnapshot(postId, userId, 0, Date.from(Instant.parse("2026-04-27T08:00:00Z")));
        PostMediaAsset removedAsset = mediaAsset(removedAssetId, userId, postId, PostMediaKind.FILE);
        List<PostContentBlockCommand> blocks = List.of();
        List<PostContentBlockCommand> normalizedBlocks = List.of();

        when(sensitiveFilter.filter("&lt;title&gt;")).thenReturn("title");
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(normalizedBlocks);
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(postMediaAssetRepository.listByPostId(postId)).thenReturn(List.of(removedAsset));

        service.updatePost(userId, postId, "<title>", categoryId, List.of("spring"), blocks);

        var ordered = inOrder(postMediaAssetRepository, postMediaStoragePort);
        ordered.verify(postMediaAssetRepository).releaseRemovedFromPost(eq(postId), eq(List.of()), any(Date.class));
        ordered.verify(postMediaStoragePort).releaseReference(removedAsset, userId);
    }

    @Test
    void updateShouldDelayStorageReleaseUntilAfterCommit() {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        UUID categoryId = uuid(2);
        UUID removedAssetId = uuid(202);
        PostSnapshot post = new PostSnapshot(postId, userId, 0, Date.from(Instant.parse("2026-04-27T08:00:00Z")));
        PostMediaAsset removedAsset = mediaAsset(removedAssetId, userId, postId, PostMediaKind.FILE);
        when(sensitiveFilter.filter("&lt;title&gt;")).thenReturn("title");
        when(blockPolicy.validateAndNormalize(List.<PostContentBlockCommand>of())).thenReturn(List.of());
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(post);
        when(postMediaAssetRepository.listByPostId(postId)).thenReturn(List.of(removedAsset));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.updatePost(userId, postId, "<title>", categoryId, List.of("spring"), List.of());

            verify(postMediaStoragePort, never()).releaseReference(removedAsset, userId);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(postMediaStoragePort).releaseReference(removedAsset, userId);
    }

    @Test
    void createShouldReleaseBoundReferenceWhenTransactionRollsBack() {
        UUID userId = uuid(7);
        UUID categoryId = uuid(1);
        UUID postId = uuid(99);
        UUID assetId = uuid(201);
        UUID referenceId = uuid(202);
        Date createTime = Date.from(Instant.parse("2026-04-27T08:00:00Z"));
        PostDraft draft = new PostDraft(userId, "title", categoryId, createTime);
        PostMediaAsset asset = uploadedAsset(assetId, userId);
        List<PostContentBlockCommand> blocks = List.of(new PostContentBlockCommand("image", "", assetId, null, "", "", null));
        List<PostContentBlockCommand> normalizedBlocks = List.of(new PostContentBlockCommand("image", "", assetId, "", "", "", null));

        when(sensitiveFilter.filter("title")).thenReturn("title");
        when(sensitiveFilter.filter("")).thenReturn("");
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(normalizedBlocks);
        when(idempotencyGuard.executeRequired(
                eq("content:create_post"),
                eq(userId),
                anyString(),
                anyString(),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(PostCreateResult.class),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<PostCreateResult>>getArgument(6).get());
        when(domainService.createDraft(userId, "title", categoryId)).thenReturn(draft);
        when(postRepository.create(draft)).thenReturn(postId);
        when(postMediaAssetRepository.listByIds(List.of(assetId))).thenReturn(List.of(asset));
        when(postMediaStoragePort.bindReference(asset, postId, userId)).thenReturn(referenceId);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.create("idem-bind-rollback", new CreatePostCommand(userId, "title", categoryId, List.of(), blocks));
            verify(postMediaStoragePort, never()).releaseReference(any(), eq(userId));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(postMediaStoragePort).releaseReference(org.mockito.ArgumentMatchers.argThat(released ->
                released != null
                        && assetId.equals(released.id())
                        && referenceId.equals(released.ossReferenceId())
                        && postId.equals(released.postId())
        ), eq(userId));
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
        assertThat(output.getAll()).doesNotContain("community.reason_code=admin_delete");
    }

    private static PostMediaAsset mediaAsset(UUID assetId, UUID ownerUserId, UUID postId, PostMediaKind mediaKind) {
        Date now = Date.from(Instant.parse("2026-04-27T08:00:00Z"));
        return new PostMediaAsset(
                assetId,
                ownerUserId,
                postId,
                uuid(901),
                uuid(902),
                uuid(903),
                null,
                "asset.bin",
                "application/octet-stream",
                1024L,
                mediaKind,
                PostMediaAssetLifecycle.BOUND,
                PostVideoState.NONE,
                "https://cdn.example.com/asset.bin",
                "",
                now,
                now
        );
    }

    private static PostMediaAsset uploadedAsset(UUID assetId, UUID ownerUserId) {
        Date now = Date.from(Instant.parse("2026-04-27T08:00:00Z"));
        return new PostMediaAsset(
                assetId,
                ownerUserId,
                null,
                uuid(901),
                uuid(902),
                null,
                uuid(903),
                "asset.bin",
                "image/png",
                1024L,
                PostMediaKind.IMAGE,
                PostMediaAssetLifecycle.UPLOADED,
                PostVideoState.NONE,
                "https://cdn.example.com/asset.bin",
                "",
                now,
                now
        );
    }
}
