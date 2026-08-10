package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.IdempotencyProperties;
import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.common.idempotency.TransactionalIdempotencyStore;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.application.CommentApplicationService.CreateCommentCommand;
import com.nowcoder.community.content.infrastructure.text.SpringHtmlContentTextCodec;
import com.nowcoder.community.content.application.ContentSanitizer;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.application.CommentApplicationService.CommentCreateResult;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentEdit;
import com.nowcoder.community.content.domain.model.CommentReplyContext;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentTransitionStatus;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.social.api.action.SocialInteractionActionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nowcoder.community.support.TestUuids.uuid;
import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class CommentApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2025-01-02T03:04:05Z"), ZoneOffset.UTC);

    private ContentSanitizer sensitiveFilter;
    private IdempotencyGuard idempotencyGuard;
    private UserModerationGuard moderationGuard;
    private CommentRepository commentRepository;
    private PostContentRepository postContentPort;
    private PostCounterCache postCounterCache;
    private CommentPageCache commentPageCache;
    private PostCacheAfterCommit postCacheAfterCommit;
    private SocialInteractionActionApi interactionActionApi;
    private ContentEventPublisher eventPublisher;
    private CommentDeletionTransactionOperations deletionOperations;
    private CommentApplicationService service;

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }

    @BeforeEach
    void setUp() {
        sensitiveFilter = mock(ContentSanitizer.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        moderationGuard = mock(UserModerationGuard.class);
        commentRepository = mock(CommentRepository.class);
        postContentPort = mock(PostContentRepository.class);
        postCounterCache = mock(PostCounterCache.class);
        commentPageCache = mock(CommentPageCache.class);
        postCacheAfterCommit = mock(PostCacheAfterCommit.class);
        interactionActionApi = mock(SocialInteractionActionApi.class);
        eventPublisher = mock(ContentEventPublisher.class);
        when(postContentPort.incrementActiveCommentCount(any(UUID.class), anyInt())).thenReturn(2L);
        deletionOperations = new CommentDeletionTransactionOperations(
                commentRepository,
                postContentPort,
                new CommentCacheAfterCommit(postCounterCache, commentPageCache, postCacheAfterCommit),
                eventPublisher,
                CLOCK
        );
        service = new CommentApplicationService(
                sensitiveFilter,
                idempotencyGuard,
                new SpringHtmlContentTextCodec(),
                moderationGuard,
                new CommentDomainService(),
                commentRepository,
                postContentPort,
                new CommentCacheAfterCommit(postCounterCache, commentPageCache, postCacheAfterCommit),
                interactionActionApi,
                eventPublisher,
                deletionOperations,
                CLOCK
        );
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void createPostCommentShouldOwnWriteOrchestration() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID commentId = uuid(200);
        DiscussPost post = post(postId, postAuthorId);

        when(idempotencyGuard.executeRequired(
                eq("content:create_comment"),
                eq(userId),
                anyString(),
                anyString(),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(UUID.class),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(6).get());
        when(postContentPort.getById(postId)).thenReturn(post);
        when(sensitiveFilter.filter("hello &amp; world")).thenReturn("clean &amp; body");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        CommentCreateResult result = service.create(
                "idem-1",
                new CreateCommentCommand(userId, postId, null, " hello & world ")
        );

        assertThat(result.commentId()).isEqualTo(commentId);
        verify(idempotencyGuard).executeRequired(
                eq("content:create_comment"),
                eq(userId),
                eq("idem-1"),
                org.mockito.ArgumentMatchers.argThat(hash -> hash != null && !hash.isBlank()),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(UUID.class),
                any()
        );

        var inOrder = inOrder(
                moderationGuard,
                postContentPort,
                interactionActionApi,
                commentRepository,
                postCounterCache,
                eventPublisher
        );
        inOrder.verify(moderationGuard).assertCanSpeak(userId);
        inOrder.verify(postContentPort).getById(postId);
        inOrder.verify(interactionActionApi).assertInteractionAllowed(userId, postAuthorId);
        ArgumentCaptor<CommentDraft> draftCaptor = ArgumentCaptor.forClass(CommentDraft.class);
        inOrder.verify(postContentPort).incrementActiveCommentCount(postId, 1);
        inOrder.verify(commentRepository).create(draftCaptor.capture());
        ArgumentCaptor<CommentPayload> eventCaptor = ArgumentCaptor.forClass(CommentPayload.class);
        inOrder.verify(eventPublisher).publishCommentCreated(eventCaptor.capture());
        inOrder.verify(postCounterCache).markDirty(postId);

        CommentDraft draft = draftCaptor.getValue();
        assertThat(draft.userId()).isEqualTo(userId);
        assertThat(draft.postId()).isEqualTo(postId);
        assertThat(draft.rootCommentId()).isNull();
        assertThat(draft.parentCommentId()).isNull();
        assertThat(draft.replyToUserId()).isNull();
        assertThat(draft.content()).isEqualTo("clean &amp; body");
        assertThat(draft.createTime()).isNotNull();

        CommentPayload event = eventCaptor.getValue();
        assertThat(event.getCommentId()).isEqualTo(commentId);
        assertThat(event.getPostId()).isEqualTo(postId);
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getEntityType()).isEqualTo(EntityTypes.POST);
        assertThat(event.getEntityId()).isEqualTo(postId);
        assertThat(event.getTargetUserId()).isEqualTo(postAuthorId);
        assertThat(event.getContent()).isEqualTo("clean & body");
        assertThat(event.getCreateTime()).isEqualTo(draft.createTime().toInstant());
        assertThat(event.getPostAggregateVersion()).isEqualTo(2L);
    }

    @Test
    void createShouldEvictCommentPageCacheAfterCommit() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID commentId = uuid(200);

        when(idempotencyGuard.executeRequired(
                eq("content:create_comment"),
                eq(userId),
                anyString(),
                anyString(),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(UUID.class),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(6).get());
        when(postContentPort.getById(postId)).thenReturn(post(postId, postAuthorId));
        when(sensitiveFilter.filter("body")).thenReturn("body");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        service.create("idem-cache-evict", new CreateCommentCommand(userId, postId, null, "body"));

        verify(commentPageCache).evictPost(postId);
    }

    @Test
    void createShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.create("idem-null", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void commentApplicationServiceShouldNotDependOnRewardOrGrowthSideEffectApis() {
        assertThat(Arrays.stream(CommentApplicationService.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .doesNotContain(
                        "com.nowcoder.community.user.api.action.UserRewardActionApi",
                        "com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi"
                );
    }

    @Test
    void commentApplicationServiceShouldNotDependOnPostWriteSideEffectScheduler() {
        assertThat(Arrays.stream(CommentApplicationService.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .doesNotContain("com.nowcoder.community.content.application.PostWriteSideEffectScheduler");
    }

    @Test
    void createShouldSaveUuidIdempotencySuccessPayloadUsingCreatedCommentId() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID commentId = uuid(200);
        TransactionalIdempotencyStore store = mock(TransactionalIdempotencyStore.class);
        IdempotencyGuard realGuard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());
        when(store.isEnlistedInCurrentTransaction()).thenReturn(true);
        when(store.tryAcquireProcessing(eq("content:create_comment"), eq(userId), eq("idem-transaction"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(store.saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(postContentPort.getById(postId)).thenReturn(post(postId, postAuthorId));
        when(sensitiveFilter.filter("hi")).thenReturn("hi");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);
        service = new CommentApplicationService(
                sensitiveFilter,
                realGuard,
                new SpringHtmlContentTextCodec(),
                moderationGuard,
                new CommentDomainService(),
                commentRepository,
                postContentPort,
                new CommentCacheAfterCommit(postCounterCache, commentPageCache, postCacheAfterCommit),
                interactionActionApi,
                eventPublisher,
                deletionOperations,
                CLOCK
        );

        CommentCreateResult result = service.create(
                "idem-transaction",
                new CreateCommentCommand(userId, postId, null, "hi")
        );

        assertThat(result.commentId()).isEqualTo(commentId);
        var inOrder = inOrder(commentRepository, store);
        inOrder.verify(commentRepository).create(any(CommentDraft.class));
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        inOrder.verify(store).saveSuccess(
                eq("content:create_comment"),
                eq(userId),
                eq("idem-transaction"),
                hashCaptor.capture(),
                jsonCaptor.capture(),
                any(Duration.class)
        );
        assertThat(hashCaptor.getValue()).isNotBlank();
        assertThat(jsonCaptor.getValue()).isEqualTo("\"" + commentId + "\"");
    }

    @Test
    void createShouldReplayRecordedResultForSameIdempotencyKeyWithoutDuplicatingFact() {
        useRealIdempotencyGuard(new InMemoryIdempotencyStore());
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID commentId = uuid(200);
        CreateCommentCommand command = new CreateCommentCommand(userId, postId, null, "body");

        when(postContentPort.getById(postId)).thenReturn(post(postId, postAuthorId));
        when(sensitiveFilter.filter("body")).thenReturn("body");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        CommentCreateResult first = service.create("idem-replay-comment", command);
        CommentCreateResult replay = service.create("idem-replay-comment", command);

        assertThat(first.commentId()).isEqualTo(commentId);
        assertThat(replay.commentId()).isEqualTo(commentId);
        verify(commentRepository, times(1)).create(any(CommentDraft.class));
        verify(postContentPort, times(1)).incrementActiveCommentCount(postId, 1);
        verify(postCounterCache, times(1)).markDirty(postId);
        verify(commentPageCache, times(1)).evictPost(postId);
        verify(postCacheAfterCommit, times(1)).evict(postId, 2L);
        verify(eventPublisher, times(1)).publishCommentCreated(any(CommentPayload.class));
    }

    @Test
    void createShouldRejectSameIdempotencyKeyWithDifferentCommentFingerprint() {
        useRealIdempotencyGuard(new InMemoryIdempotencyStore());
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID commentId = uuid(200);

        when(postContentPort.getById(postId)).thenReturn(post(postId, postAuthorId));
        when(sensitiveFilter.filter("body")).thenReturn("body");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        service.create("idem-conflict-comment", new CreateCommentCommand(userId, postId, null, "body"));

        assertThatThrownBy(() -> service.create(
                "idem-conflict-comment",
                new CreateCommentCommand(userId, postId, null, "changed body")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ContentErrorCode.REQUEST_REPLAY_CONFLICT));
        verify(commentRepository, times(1)).create(any(CommentDraft.class));
        verify(eventPublisher, times(1)).publishCommentCreated(any(CommentPayload.class));
    }

    @Test
    void commentApplicationServiceShouldNotOwnDedicatedCommentWriteTransactionTemplate() {
        assertThat(Arrays.stream(CommentApplicationService.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .doesNotContain(
                        "org.springframework.transaction.support.TransactionTemplate",
                        "org.springframework.transaction.PlatformTransactionManager"
                );
    }

    @Test
    void createRootReplyShouldDeriveStoredAndNotificationTargetsFromLockedRoot() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID rootCommentId = uuid(200);
        UUID rootAuthorId = uuid(201);
        UUID commentId = uuid(300);
        CommentSnapshot root = rootComment(rootCommentId, rootAuthorId, postId);

        when(idempotencyGuard.executeRequired(
                eq("content:create_comment"),
                eq(userId),
                anyString(),
                anyString(),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(UUID.class),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(6).get());
        when(postContentPort.getById(postId)).thenReturn(post(postId, uuid(2)));
        when(commentRepository.lockReplyContext(postId, rootCommentId))
                .thenReturn(Optional.of(new CommentReplyContext(root, root)));
        when(sensitiveFilter.filter("reply")).thenReturn("reply");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        service.create(
                "idem-reply-target",
                new CreateCommentCommand(userId, postId, rootCommentId, "reply")
        );

        var order = inOrder(commentRepository, interactionActionApi, sensitiveFilter);
        order.verify(commentRepository).lockReplyContext(postId, rootCommentId);
        order.verify(interactionActionApi).assertInteractionAllowed(userId, rootAuthorId);
        order.verify(sensitiveFilter).filter("reply");
        ArgumentCaptor<CommentDraft> draftCaptor = ArgumentCaptor.forClass(CommentDraft.class);
        verify(commentRepository).create(draftCaptor.capture());
        assertThat(draftCaptor.getValue().postId()).isEqualTo(postId);
        assertThat(draftCaptor.getValue().rootCommentId()).isEqualTo(rootCommentId);
        assertThat(draftCaptor.getValue().parentCommentId()).isEqualTo(rootCommentId);
        assertThat(draftCaptor.getValue().replyToUserId()).isEqualTo(rootAuthorId);
        ArgumentCaptor<CommentPayload> eventCaptor = ArgumentCaptor.forClass(CommentPayload.class);
        verify(eventPublisher).publishCommentCreated(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEntityType()).isEqualTo(EntityTypes.COMMENT);
        assertThat(eventCaptor.getValue().getEntityId()).isEqualTo(rootCommentId);
        assertThat(eventCaptor.getValue().getTargetUserId()).isEqualTo(rootAuthorId);
    }

    @Test
    void createNestedReplyShouldUseDirectParentForStorageBlockCheckAndEvent() {
        UUID userId = uuid(7);
        UUID postId = uuid(100);
        UUID rootCommentId = uuid(200);
        UUID rootAuthorId = uuid(8);
        UUID directParentId = uuid(201);
        UUID directParentAuthorId = uuid(9);
        UUID commentId = uuid(300);
        CommentSnapshot root = rootComment(rootCommentId, rootAuthorId, postId);
        CommentSnapshot directParent = replyComment(
                directParentId,
                directParentAuthorId,
                postId,
                rootCommentId,
                rootAuthorId
        );

        when(idempotencyGuard.executeRequired(
                eq("content:create_comment"),
                eq(userId),
                anyString(),
                anyString(),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(UUID.class),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(6).get());
        when(postContentPort.getById(postId)).thenReturn(post(postId, uuid(2)));
        when(commentRepository.lockReplyContext(postId, directParentId))
                .thenReturn(Optional.of(new CommentReplyContext(directParent, root)));
        when(sensitiveFilter.filter("reply")).thenReturn("reply");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        service.create("idem-parent-reply", new CreateCommentCommand(userId, postId, directParentId, "reply"));

        verify(interactionActionApi).assertInteractionAllowed(userId, directParentAuthorId);
        verify(commentRepository).create(org.mockito.ArgumentMatchers.argThat(draft ->
                postId.equals(draft.postId())
                        && rootCommentId.equals(draft.rootCommentId())
                        && directParentId.equals(draft.parentCommentId())
                        && directParentAuthorId.equals(draft.replyToUserId())));
        ArgumentCaptor<CommentPayload> event = ArgumentCaptor.forClass(CommentPayload.class);
        verify(eventPublisher).publishCommentCreated(event.capture());
        assertThat(event.getValue().getEntityType()).isEqualTo(EntityTypes.COMMENT);
        assertThat(event.getValue().getEntityId()).isEqualTo(directParentId);
        assertThat(event.getValue().getTargetUserId()).isEqualTo(directParentAuthorId);
    }

    @Test
    void createReplyShouldNotTreatMissingLockedParentAsTopLevelComment() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID directParentId = uuid(200);

        when(idempotencyGuard.executeRequired(
                eq("content:create_comment"),
                eq(userId),
                anyString(),
                anyString(),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(UUID.class),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(6).get());
        when(postContentPort.getById(postId)).thenReturn(post(postId, postAuthorId));
        when(commentRepository.lockReplyContext(postId, directParentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                "idem-2",
                new CreateCommentCommand(userId, postId, directParentId, "reply")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));

        verify(interactionActionApi, never()).assertInteractionAllowed(any(), any());
        verify(sensitiveFilter, never()).filter(anyString());
        verify(commentRepository, never()).create(any(CommentDraft.class));
        verify(postContentPort, never()).incrementActiveCommentCount(any(UUID.class), anyInt());
        verify(eventPublisher, never()).publishCommentCreated(any(CommentPayload.class));
    }

    @Test
    void createCommentShouldRejectEitherBlockedBeforePersistence() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);

        when(idempotencyGuard.executeRequired(
                eq("content:create_comment"),
                eq(userId),
                anyString(),
                anyString(),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(UUID.class),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(6).get());
        when(postContentPort.getById(postId)).thenReturn(post(postId, postAuthorId));
        doThrow(new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作"))
                .when(interactionActionApi).assertInteractionAllowed(userId, postAuthorId);

        assertThatThrownBy(() -> service.create(
                "idem-3",
                new CreateCommentCommand(userId, postId, null, "blocked")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        verify(commentRepository, never()).create(any(CommentDraft.class));
        verify(postContentPort, never()).incrementActiveCommentCount(any(UUID.class), anyInt());
        verify(eventPublisher, never()).publishCommentCreated(any(CommentPayload.class));
    }

    @Test
    void updateCommentShouldSanitizeAndPersistThroughRepository() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        CommentSnapshot existing = rootComment(commentId, userId, postId);

        when(postContentPort.getById(postId)).thenReturn(post(postId, uuid(2)));
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        when(sensitiveFilter.filter("hello &amp; world")).thenReturn("clean");
        when(commentRepository.apply(any(CommentEdit.class))).thenReturn(CommentTransitionStatus.APPLIED);

        beginTransactionSynchronization();
        service.updateComment(userId, postId, commentId, " hello & world ");

        var inOrder = inOrder(moderationGuard, postContentPort, commentRepository);
        inOrder.verify(moderationGuard).assertCanSpeak(userId);
        inOrder.verify(postContentPort).getById(postId);
        inOrder.verify(commentRepository).getRequiredSnapshot(commentId);
        ArgumentCaptor<CommentEdit> edit = ArgumentCaptor.forClass(CommentEdit.class);
        inOrder.verify(commentRepository).apply(edit.capture());
        inOrder.verify(postContentPort).incrementActiveCommentCount(postId, 0);
        assertThat(edit.getValue().commentId()).isEqualTo(commentId);
        assertThat(edit.getValue().expectedVersion()).isEqualTo(existing.version());
        assertThat(edit.getValue().content()).isEqualTo("clean");
        verifyNoInteractions(postCounterCache, commentPageCache);

        commitTransactionSynchronization();

        verify(commentPageCache).evictPost(postId);
        verify(postCacheAfterCommit).evictSummaryAndDetail(postId, 2L);
        verifyNoInteractions(postCounterCache);
    }

    @Test
    void deleteByAuthorShouldDeleteActiveCommentThreadAndApplySideEffects() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        UUID replyId = uuid(201);
        UUID nestedReplyId = uuid(202);
        CommentSnapshot existing = rootComment(commentId, userId, postId);
        List<CommentSnapshot> affected = List.of(
                rootComment(commentId, userId, postId),
                replyComment(replyId, userId, postId, commentId, userId),
                replyComment(nestedReplyId, userId, postId, commentId, userId)
        );
        when(commentRepository.findSnapshot(commentId)).thenReturn(Optional.of(existing));
        when(commentRepository.apply(any(CommentDeletion.class)))
                .thenReturn(CommentDeletionResult.applied(affected));

        beginTransactionSynchronization();
        service.deleteByAuthor(userId, postId, commentId);

        var inOrder = inOrder(commentRepository, postContentPort);
        inOrder.verify(commentRepository).findSnapshot(commentId);
        inOrder.verify(commentRepository).apply(any(CommentDeletion.class));
        inOrder.verify(postContentPort).incrementActiveCommentCount(postId, -3);
        verifyNoInteractions(postCounterCache, commentPageCache);

        commitTransactionSynchronization();

        verify(postCounterCache).markDirty(postId);
        verify(commentPageCache).evictPost(postId);
        verify(postCacheAfterCommit).evict(postId, 2L);
    }

    @Test
    void deleteByAuthorShouldPublishDeleteEventForEveryActuallyDeletedComment() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        UUID replyId = uuid(201);
        UUID nestedReplyId = uuid(202);
        CommentSnapshot existing = rootComment(commentId, userId, postId);
        when(commentRepository.findSnapshot(commentId)).thenReturn(Optional.of(existing));
        UUID replyAuthorId = uuid(2);
        UUID nestedReplyAuthorId = uuid(3);
        List<CommentSnapshot> affected = List.of(
                rootComment(commentId, userId, postId),
                replyComment(replyId, replyAuthorId, postId, commentId, userId),
                replyComment(nestedReplyId, nestedReplyAuthorId, postId, commentId, replyAuthorId)
        );
        when(commentRepository.apply(any(CommentDeletion.class)))
                .thenReturn(CommentDeletionResult.applied(affected));

        service.deleteByAuthor(userId, postId, commentId);

        ArgumentCaptor<CommentPayload> eventCaptor = ArgumentCaptor.forClass(CommentPayload.class);
        verify(eventPublisher, times(3)).publishCommentDeleted(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(CommentPayload::getCommentId)
                .containsExactly(commentId, replyId, nestedReplyId);
        assertThat(eventCaptor.getAllValues()).extracting(CommentPayload::getPostId)
                .containsExactly(postId, postId, postId);
        assertThat(eventCaptor.getAllValues()).extracting(CommentPayload::getUserId)
                .containsExactly(userId, replyAuthorId, nestedReplyAuthorId);
        assertThat(eventCaptor.getAllValues()).extracting(CommentPayload::getEntityType)
                .containsExactly(EntityTypes.POST, EntityTypes.COMMENT, EntityTypes.COMMENT);
        assertThat(eventCaptor.getAllValues()).extracting(CommentPayload::getEntityId)
                .containsExactly(postId, commentId, commentId);
        assertThat(eventCaptor.getAllValues()).extracting(CommentPayload::getPostAggregateVersion)
                .containsOnly(2L);
        assertThat(eventCaptor.getAllValues()).allSatisfy(event -> assertThat(event.getCreateTime()).isNotNull());
    }

    @Test
    void deleteByAuthorShouldRejectNestedReplyWhenRoutePostDoesNotMatchRootPost() {
        UUID userId = uuid(1);
        UUID actualPostId = uuid(100);
        UUID routePostId = uuid(101);
        UUID parentCommentId = uuid(200);
        UUID replyId = uuid(201);
        CommentSnapshot reply = replyComment(replyId, userId, actualPostId, parentCommentId, uuid(2));
        when(commentRepository.findSnapshot(replyId)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> service.deleteByAuthor(userId, routePostId, replyId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));

        verify(commentRepository, never()).apply(any(CommentDeletion.class));
        verify(postContentPort, never()).incrementActiveCommentCount(any(UUID.class), anyInt());
    }

    @Test
    void deleteByAuthorShouldUseActuallyDeletedCommentsForCountAndEvents() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        UUID replyId = uuid(201);
        UUID nestedReplyId = uuid(202);
        CommentSnapshot existing = rootComment(commentId, userId, postId);
        List<CommentSnapshot> activeThread = List.of(
                rootComment(commentId, userId, postId),
                replyComment(nestedReplyId, userId, postId, commentId, userId)
        );
        when(commentRepository.findSnapshot(commentId)).thenReturn(Optional.of(existing));
        when(commentRepository.apply(any(CommentDeletion.class)))
                .thenReturn(CommentDeletionResult.applied(activeThread));

        service.deleteByAuthor(userId, postId, commentId);

        verify(postContentPort).incrementActiveCommentCount(postId, -2);
        ArgumentCaptor<CommentPayload> events = ArgumentCaptor.forClass(CommentPayload.class);
        verify(eventPublisher, times(2)).publishCommentDeleted(events.capture());
        assertThat(events.getAllValues())
                .extracting(CommentPayload::getCommentId)
                .containsExactly(commentId, nestedReplyId)
                .doesNotContain(replyId);
    }

    @Test
    void deleteByAuthorShouldSkipSideEffectsWhenNoCommentsChanged() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        CommentSnapshot existing = rootComment(commentId, userId, postId);
        when(commentRepository.findSnapshot(commentId)).thenReturn(Optional.of(existing));
        when(commentRepository.apply(any(CommentDeletion.class))).thenReturn(CommentDeletionResult.noOp());

        service.deleteByAuthor(userId, postId, commentId);

        verify(postContentPort, never()).incrementActiveCommentCount(any(UUID.class), anyInt());
        verify(eventPublisher, never()).publishCommentDeleted(any());
    }

    @Test
    void boundedModeratorDeletionShouldSurfaceStaleInsteadOfRecordingSuccess() {
        UUID moderatorId = uuid(91);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        CommentDeletionTransactionOperations deletionOperations =
                useBoundedDeletionOperations();
        when(commentRepository.findSnapshot(commentId))
                .thenReturn(Optional.of(rootComment(commentId, uuid(1), postId)));
        when(deletionOperations.deleteRoot(any(CommentDeletion.class), eq(postId)))
                .thenReturn(CommentDeletionResult.stale());

        assertThatThrownBy(() -> service.deleteByModeration(
                moderatorId, commentId, "hide: spam"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("comment transition stale");

        verify(deletionOperations, never()).deleteReplyBatch(
                any(), any(), any(), anyString(), any(), anyInt());
    }

    @Test
    void boundedModeratorDeletionShouldSurfaceMissingTarget() {
        UUID moderatorId = uuid(91);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        CommentDeletionTransactionOperations deletionOperations =
                useBoundedDeletionOperations();
        when(commentRepository.findSnapshot(commentId))
                .thenReturn(Optional.of(rootComment(commentId, uuid(1), postId)));
        when(deletionOperations.deleteRoot(any(CommentDeletion.class), eq(postId)))
                .thenReturn(CommentDeletionResult.notFound());

        assertThatThrownBy(() -> service.deleteByModeration(
                moderatorId, commentId, "hide: spam"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ContentErrorCode.COMMENT_NOT_FOUND));

        verify(deletionOperations, never()).deleteReplyBatch(
                any(), any(), any(), anyString(), any(), anyInt());
    }

    private static DiscussPost post(UUID postId, UUID authorId) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(authorId);
        return post;
    }

    private static CommentSnapshot rootComment(UUID commentId, UUID userId, UUID postId) {
        return new CommentSnapshot(
                commentId,
                userId,
                postId,
                commentId,
                null,
                null,
                "content",
                0,
                new Date(),
                null,
                0,
                null,
                null,
                null,
                7L
        );
    }

    private static CommentSnapshot replyComment(
            UUID commentId,
            UUID userId,
            UUID postId,
            UUID rootCommentId,
            UUID replyToUserId
    ) {
        return new CommentSnapshot(
                commentId,
                userId,
                postId,
                rootCommentId,
                rootCommentId,
                replyToUserId,
                "content",
                0,
                new Date(),
                null,
                0,
                null,
                null,
                null,
                7L
        );
    }

    private void useRealIdempotencyGuard(IdempotencyStore store) {
        idempotencyGuard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());
        service = new CommentApplicationService(
                sensitiveFilter,
                idempotencyGuard,
                new SpringHtmlContentTextCodec(),
                moderationGuard,
                new CommentDomainService(),
                commentRepository,
                postContentPort,
                new CommentCacheAfterCommit(postCounterCache, commentPageCache, postCacheAfterCommit),
                interactionActionApi,
                eventPublisher,
                deletionOperations,
                CLOCK
        );
    }

    private CommentDeletionTransactionOperations useBoundedDeletionOperations() {
        CommentDeletionTransactionOperations deletionOperations =
                mock(CommentDeletionTransactionOperations.class);
        service = new CommentApplicationService(
                sensitiveFilter,
                idempotencyGuard,
                new SpringHtmlContentTextCodec(),
                moderationGuard,
                new CommentDomainService(),
                commentRepository,
                postContentPort,
                new CommentCacheAfterCommit(postCounterCache, commentPageCache, postCacheAfterCommit),
                interactionActionApi,
                eventPublisher,
                deletionOperations,
                CLOCK
        );
        return deletionOperations;
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private void commitTransactionSynchronization() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }

    private static final class InMemoryIdempotencyStore implements TransactionalIdempotencyStore {

        private final Map<String, Entry> entries = new HashMap<>();

        @Override
        public boolean isEnlistedInCurrentTransaction() {
            return true;
        }

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
