package com.nowcoder.community.content.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.content.application.command.CreatePostCommand;
import com.nowcoder.community.content.application.command.PostContentBlockCommand;
import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import com.nowcoder.community.content.application.result.PostCreateResult;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostMediaReferenceOperation;
import com.nowcoder.community.content.domain.model.PostMediaReferenceStatus;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.CategoryRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.repository.PostTagRepository;
import com.nowcoder.community.content.domain.service.PostContentBlockPolicy;
import com.nowcoder.community.content.domain.service.PostPublishingDomainService;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.content.infrastructure.text.SpringHtmlContentTextCodec;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostPublishingMediaDesiredStateTest {

    private static final Instant NOW = Instant.parse("2026-07-15T09:00:00Z");

    private final Map<Class<?>, Object> collaborators = new HashMap<>();
    private ContentSanitizer sanitizer;
    private IdempotencyGuard idempotencyGuard;
    private UserModerationGuard moderationGuard;
    private PostPublishingDomainService domainService;
    private PostContentBlockPolicy blockPolicy;
    private PostRepository postRepository;
    private PostContentBlockRepository blockRepository;
    private PostMediaAssetRepository mediaRepository;
    private PostMediaStoragePort storagePort;
    private PostMediaReferenceCommandPublisher commandPublisher;
    private CategoryRepository categoryRepository;
    private PostTagRepository tagRepository;
    private PostDomainEventPublisher eventPublisher;
    private PostPublishingApplicationService service;

    @BeforeEach
    void setUp() {
        sanitizer = register(ContentSanitizer.class, mock(ContentSanitizer.class));
        idempotencyGuard = register(IdempotencyGuard.class, mock(IdempotencyGuard.class));
        moderationGuard = register(UserModerationGuard.class, mock(UserModerationGuard.class));
        domainService = register(PostPublishingDomainService.class, mock(PostPublishingDomainService.class));
        blockPolicy = register(PostContentBlockPolicy.class, mock(PostContentBlockPolicy.class));
        postRepository = register(PostRepository.class, mock(PostRepository.class));
        blockRepository = register(PostContentBlockRepository.class, mock(PostContentBlockRepository.class));
        mediaRepository = register(PostMediaAssetRepository.class, mock(PostMediaAssetRepository.class));
        storagePort = register(PostMediaStoragePort.class, mock(PostMediaStoragePort.class));
        commandPublisher = register(PostMediaReferenceCommandPublisher.class, mock(PostMediaReferenceCommandPublisher.class));
        categoryRepository = register(CategoryRepository.class, mock(CategoryRepository.class));
        tagRepository = register(PostTagRepository.class, mock(PostTagRepository.class));
        eventPublisher = register(PostDomainEventPublisher.class, mock(PostDomainEventPublisher.class));
        register(ContentTextCodec.class, new SpringHtmlContentTextCodec());
        register(PostBusinessEventLogger.class, new PostBusinessEventLogger());
        register(Clock.class, Clock.fixed(NOW, ZoneOffset.UTC));
        register(UuidV7Generator.class, new UuidV7Generator(Clock.fixed(NOW, ZoneOffset.UTC)));
        when(sanitizer.filter(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyGuard.executeRequired(
                anyString(),
                any(UUID.class),
                anyString(),
                anyString(),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(PostCreateResult.class),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<PostCreateResult>>getArgument(6).get());
        service = constructService();
    }

    @Test
    void createShouldPersistBindIntentAndDurableCommandWithoutCallingOss() {
        UUID userId = uuid(7);
        UUID categoryId = uuid(1);
        UUID postId = uuid(101);
        UUID assetId = uuid(201);
        UUID legacyReferenceId = uuid(901);
        PostMediaAsset asset = asset(
                assetId,
                userId,
                null,
                null,
                PostMediaAssetLifecycle.UPLOADED,
                PostMediaReferenceStatus.UNBOUND,
                0L
        );
        List<PostContentBlockCommand> blocks = List.of(mediaBlock(assetId));
        PostDraft draft = new PostDraft(userId, "title", categoryId, Date.from(NOW));
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(blocks);
        when(domainService.createDraft(userId, "title", categoryId)).thenReturn(draft);
        when(postRepository.create(draft)).thenReturn(postId);
        when(mediaRepository.listByIds(List.of(assetId))).thenReturn(List.of(asset));
        when(mediaRepository.requestBind(
                eq(assetId),
                eq(postId),
                any(UUID.class),
                eq(PostVideoState.NONE),
                any(Date.class)
        )).thenReturn(7L);
        when(storagePort.bindReference(asset, postId, userId)).thenReturn(legacyReferenceId);

        service.create("create-with-media", new CreatePostCommand(
                userId,
                "title",
                categoryId,
                List.of(),
                blocks
        ));

        ArgumentCaptor<UUID> referenceId = ArgumentCaptor.forClass(UUID.class);
        verify(mediaRepository).requestBind(
                eq(assetId),
                eq(postId),
                referenceId.capture(),
                eq(PostVideoState.NONE),
                any(Date.class)
        );
        assertThat(referenceId.getValue()).isNotNull();
        ArgumentCaptor<PostMediaReferenceCommand> command =
                ArgumentCaptor.forClass(PostMediaReferenceCommand.class);
        verify(commandPublisher).publish(command.capture());
        assertThat(command.getValue()).isEqualTo(new PostMediaReferenceCommand(
                assetId,
                PostMediaReferenceOperation.BIND,
                7L,
                userId
        ));
        verifyNoInteractions(storagePort);
        assertThat(mediaRepositoryInvocationNames()).doesNotContain("bindToPost");
    }

    @Test
    void updateShouldReleaseOnlyRemovedAssetAndKeepExistingAssetBound() {
        UUID userId = uuid(7);
        UUID postId = uuid(102);
        UUID categoryId = uuid(2);
        UUID keptAssetId = uuid(202);
        UUID removedAssetId = uuid(203);
        PostMediaAsset kept = boundAsset(keptAssetId, userId, postId, 4L);
        PostMediaAsset removed = boundAsset(removedAssetId, userId, postId, 8L);
        List<PostContentBlockCommand> blocks = List.of(mediaBlock(keptAssetId));
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(blocks);
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(activePost(postId, userId));
        when(mediaRepository.listByIds(List.of(keptAssetId))).thenReturn(List.of(kept));
        when(mediaRepository.listByPostId(postId)).thenReturn(List.of(kept, removed));
        when(mediaRepository.requestRelease(eq(removedAssetId), any(Date.class))).thenReturn(9L);

        service.updatePost(userId, postId, "title", categoryId, List.of(), blocks);

        verify(mediaRepository, never()).requestRelease(eq(keptAssetId), any(Date.class));
        verify(mediaRepository).requestRelease(eq(removedAssetId), any(Date.class));
        ArgumentCaptor<PostMediaReferenceCommand> command =
                ArgumentCaptor.forClass(PostMediaReferenceCommand.class);
        verify(commandPublisher).publish(command.capture());
        assertThat(command.getValue()).isEqualTo(new PostMediaReferenceCommand(
                removedAssetId,
                PostMediaReferenceOperation.RELEASE,
                9L,
                userId
        ));
        verifyNoInteractions(storagePort);
        assertThat(mediaRepositoryInvocationNames()).doesNotContain("releaseRemovedFromPost");
    }

    @Test
    void repeatedIdenticalEditShouldNotCreateANewerBindOperationOrDuplicateCommand() {
        UUID userId = uuid(7);
        UUID postId = uuid(103);
        UUID categoryId = uuid(2);
        UUID assetId = uuid(204);
        UUID requestedReferenceId = uuid(904);
        PostMediaAsset uploaded = asset(
                assetId,
                userId,
                null,
                null,
                PostMediaAssetLifecycle.UPLOADED,
                PostMediaReferenceStatus.UNBOUND,
                0L
        );
        PostMediaAsset pending = asset(
                assetId,
                userId,
                postId,
                requestedReferenceId,
                PostMediaAssetLifecycle.UPLOADED,
                PostMediaReferenceStatus.BIND_PENDING,
                5L
        );
        List<PostContentBlockCommand> blocks = List.of(mediaBlock(assetId));
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(blocks);
        when(postRepository.getRequiredSnapshot(postId)).thenReturn(activePost(postId, userId));
        when(mediaRepository.listByIds(List.of(assetId))).thenReturn(List.of(uploaded), List.of(pending));
        when(mediaRepository.listByPostId(postId)).thenReturn(List.of(pending));
        when(mediaRepository.requestBind(
                eq(assetId),
                eq(postId),
                any(UUID.class),
                eq(PostVideoState.NONE),
                any(Date.class)
        )).thenReturn(5L);
        when(storagePort.bindReference(any(), eq(postId), eq(userId))).thenReturn(requestedReferenceId);

        service.updatePost(userId, postId, "title", categoryId, List.of(), blocks);
        service.updatePost(userId, postId, "title", categoryId, List.of(), blocks);

        verify(mediaRepository, times(1)).requestBind(
                eq(assetId),
                eq(postId),
                any(UUID.class),
                eq(PostVideoState.NONE),
                any(Date.class)
        );
        verify(commandPublisher, times(1)).publish(new PostMediaReferenceCommand(
                assetId,
                PostMediaReferenceOperation.BIND,
                5L,
                userId
        ));
        verifyNoInteractions(storagePort);
    }

    @Test
    void publishingServiceShouldNotOwnRemoteCompensationOrTransactionSynchronization() {
        Set<String> dependencies = new ClassFileImporter()
                .importClasses(PostPublishingApplicationService.class)
                .get(PostPublishingApplicationService.class)
                .getDirectDependenciesFromSelf().stream()
                .map(dependency -> dependency.getTargetClass().getName())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(dependencies)
                .doesNotContain(
                        PostMediaStoragePort.class.getName(),
                        "com.nowcoder.community.common.tx.AfterCommitExecutor",
                        "org.springframework.transaction.support.TransactionSynchronization",
                        "org.springframework.transaction.support.TransactionSynchronizationManager"
                );
        assertThat(Arrays.stream(PostPublishingApplicationService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain("releaseBoundReference", "registerRollbackReferenceRelease");
    }

    private PostPublishingApplicationService constructService() {
        Constructor<?> constructor = Arrays.stream(PostPublishingApplicationService.class.getConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();
        Object[] arguments = Arrays.stream(constructor.getParameterTypes())
                .map(this::requiredCollaborator)
                .toArray();
        try {
            return (PostPublishingApplicationService) constructor.newInstance(arguments);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new AssertionError("cannot construct PostPublishingApplicationService", e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("PostPublishingApplicationService constructor failed", e.getCause());
        }
    }

    private Object requiredCollaborator(Class<?> type) {
        Object collaborator = collaborators.get(type);
        if (collaborator != null) {
            return collaborator;
        }
        throw new AssertionError("missing test collaborator for " + type.getName());
    }

    private <T> T register(Class<T> type, T collaborator) {
        collaborators.put(type, collaborator);
        return collaborator;
    }

    private List<String> mediaRepositoryInvocationNames() {
        return mockingDetails(mediaRepository).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName())
                .toList();
    }

    private PostContentBlockCommand mediaBlock(UUID assetId) {
        return new PostContentBlockCommand("image", "", assetId, "", "", "", Map.of());
    }

    private PostSnapshot activePost(UUID postId, UUID userId) {
        return new PostSnapshot(postId, userId, 0, Date.from(NOW));
    }

    private PostMediaAsset boundAsset(UUID assetId, UUID ownerId, UUID postId, long version) {
        return asset(
                assetId,
                ownerId,
                postId,
                uuid(900 + Math.toIntExact(version)),
                PostMediaAssetLifecycle.BOUND,
                PostMediaReferenceStatus.BOUND,
                version
        );
    }

    private PostMediaAsset asset(
            UUID assetId,
            UUID ownerId,
            UUID postId,
            UUID referenceId,
            PostMediaAssetLifecycle lifecycle,
            PostMediaReferenceStatus referenceStatus,
            long operationVersion
    ) {
        return new PostMediaAsset(
                assetId,
                ownerId,
                postId,
                uuid(801),
                uuid(802),
                referenceId,
                uuid(803),
                "cover.png",
                "image/png",
                256L,
                PostMediaKind.IMAGE,
                lifecycle,
                referenceStatus,
                operationVersion,
                Date.from(NOW),
                PostVideoState.NONE,
                "https://cdn.example.com/cover.png",
                "",
                Date.from(NOW),
                Date.from(NOW)
        );
    }
}
