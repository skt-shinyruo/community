package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.IdempotencyProperties;
import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.application.command.CreatePostCommand;
import com.nowcoder.community.content.application.command.PostContentBlockCommand;
import com.nowcoder.community.content.application.result.PostCreateResult;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private PostMediaReferenceCommandPublisher mediaReferenceCommandPublisher;
    private CategoryRepository categoryRepository;
    private PostTagRepository postTagRepository;
    private PostDomainEventPublisher domainEventPublisher;
    private PostPublishingApplicationService service;

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }

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
        mediaReferenceCommandPublisher = mock(PostMediaReferenceCommandPublisher.class);
        categoryRepository = mock(CategoryRepository.class);
        postTagRepository = mock(PostTagRepository.class);
        domainEventPublisher = mock(PostDomainEventPublisher.class);
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
                mediaReferenceCommandPublisher,
                categoryRepository,
                postTagRepository,
                domainEventPublisher,
                Clock.systemUTC()
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
                domainEventPublisher
        );
        inOrder.verify(moderationGuard).assertCanSpeak(userId);
        inOrder.verify(categoryRepository).assertExists(categoryId);
        inOrder.verify(domainService).createDraft(userId, "title", categoryId);
        inOrder.verify(postRepository).create(draft);
        inOrder.verify(postContentBlockRepository).replaceBlocks(eq(postId), any());
        inOrder.verify(postTagRepository).bindTagsToPost(postId, List.of("java"));
        inOrder.verify(domainEventPublisher).postPublished(postId);
        assertThat(output.getAll())
                .contains("community.post_category_id=" + categoryId)
                .contains("community.target_id=" + postId);
    }

    @Test
    void createShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.create("idem-null", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void createShouldReplayRecordedResultForSameIdempotencyKeyWithoutDuplicatingFact() {
        useRealIdempotencyGuard(new InMemoryIdempotencyStore());
        UUID userId = uuid(7);
        UUID categoryId = uuid(1);
        UUID postId = uuid(99);
        Date createTime = Date.from(Instant.parse("2026-04-27T08:00:00Z"));
        PostDraft draft = new PostDraft(userId, "title", categoryId, createTime);
        List<PostContentBlockCommand> blocks = List.of(new PostContentBlockCommand("paragraph", "body", null, null, "", "", null));
        List<PostContentBlockCommand> normalizedBlocks = List.of(new PostContentBlockCommand("paragraph", "body", null, "", "", "", null));
        CreatePostCommand command = new CreatePostCommand(userId, "title", categoryId, List.of("java"), blocks);

        when(sensitiveFilter.filter("title")).thenReturn("title");
        when(sensitiveFilter.filter("body")).thenReturn("body");
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(normalizedBlocks);
        when(domainService.createDraft(userId, "title", categoryId)).thenReturn(draft);
        when(postRepository.create(draft)).thenReturn(postId);

        PostCreateResult first = service.create("idem-replay-post", command);
        PostCreateResult replay = service.create("idem-replay-post", command);

        assertThat(first.postId()).isEqualTo(postId);
        assertThat(replay.postId()).isEqualTo(postId);
        verify(postRepository, times(1)).create(draft);
        verify(postContentBlockRepository, times(1)).replaceBlocks(eq(postId), any());
        verify(postTagRepository, times(1)).bindTagsToPost(postId, List.of("java"));
        verify(domainEventPublisher, times(1)).postPublished(postId);
    }

    @Test
    void createShouldRejectSameIdempotencyKeyWithDifferentPostFingerprint() {
        useRealIdempotencyGuard(new InMemoryIdempotencyStore());
        UUID userId = uuid(7);
        UUID categoryId = uuid(1);
        UUID postId = uuid(99);
        Date createTime = Date.from(Instant.parse("2026-04-27T08:00:00Z"));
        PostDraft draft = new PostDraft(userId, "title", categoryId, createTime);
        List<PostContentBlockCommand> blocks = List.of(new PostContentBlockCommand("paragraph", "body", null, null, "", "", null));
        List<PostContentBlockCommand> normalizedBlocks = List.of(new PostContentBlockCommand("paragraph", "body", null, "", "", "", null));

        when(sensitiveFilter.filter("title")).thenReturn("title");
        when(sensitiveFilter.filter("body")).thenReturn("body");
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(normalizedBlocks);
        when(domainService.createDraft(userId, "title", categoryId)).thenReturn(draft);
        when(postRepository.create(draft)).thenReturn(postId);

        service.create("idem-conflict-post", new CreatePostCommand(userId, "title", categoryId, List.of("java"), blocks));

        assertThatThrownBy(() -> service.create(
                "idem-conflict-post",
                new CreatePostCommand(userId, "changed title", categoryId, List.of("java"), blocks)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ContentErrorCode.REQUEST_REPLAY_CONFLICT));
        verify(postRepository, times(1)).create(draft);
        verify(domainEventPublisher, times(1)).postPublished(postId);
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
        verify(postTagRepository).replaceTagsForPost(postId, List.of("spring"));
        verify(domainEventPublisher).postUpdated(postId);
        verify(domainService).assertDeletableByAuthor(post, userId);
        verify(postRepository).markDeletedByAuthor(eq(postId), eq(userId), any(Date.class));
        verify(domainEventPublisher).postDeleted(postId);
        assertThat(output.getAll())
                .contains("community.post_category_id=" + categoryId)
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
        assertThat(output.getAll()).doesNotContain("community.reason_code=admin_delete");
    }

    @Test
    void postPublishingApplicationServiceShouldNotDependOnPostWriteSideEffectScheduler() {
        assertThat(java.util.Arrays.stream(PostPublishingApplicationService.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .doesNotContain("com.nowcoder.community.content.application.PostWriteSideEffectScheduler");
    }

    private void useRealIdempotencyGuard(IdempotencyStore store) {
        idempotencyGuard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());
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
                mediaReferenceCommandPublisher,
                categoryRepository,
                postTagRepository,
                domainEventPublisher,
                Clock.systemUTC()
        );
    }

    private static final class InMemoryIdempotencyStore implements IdempotencyStore {

        private final Map<String, Entry> entries = new HashMap<>();

        @Override
        public boolean tryAcquireProcessing(String operation, UUID userId, String key, String requestHash, Duration ttl) {
            String storageKey = storageKey(operation, userId, key);
            if (entries.containsKey(storageKey)) {
                return false;
            }
            entries.put(storageKey, new Entry(Status.PROCESSING, null, requestHash));
            return true;
        }

        @Override
        public Entry get(String operation, UUID userId, String key) {
            return entries.get(storageKey(operation, userId, key));
        }

        @Override
        public boolean saveSuccess(String operation, UUID userId, String key, String requestHash, String successJson, Duration ttl) {
            entries.put(storageKey(operation, userId, key), new Entry(Status.SUCCESS, successJson, requestHash));
            return true;
        }

        @Override
        public void extendProcessing(String operation, UUID userId, String key, Duration ttl) {
        }

        @Override
        public void delete(String operation, UUID userId, String key) {
            entries.remove(storageKey(operation, userId, key));
        }

        private String storageKey(String operation, UUID userId, String key) {
            return operation + "|" + userId + "|" + key;
        }
    }
}
