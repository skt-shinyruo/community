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
import com.nowcoder.community.content.application.command.CreateCommentCommand;
import com.nowcoder.community.content.application.command.UpdateCommentCommand;
import com.nowcoder.community.content.infrastructure.text.SpringHtmlContentTextCodec;
import com.nowcoder.community.content.application.ContentSanitizer;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.application.result.CommentCreateResult;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDeletedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentEdit;
import com.nowcoder.community.content.domain.model.CommentReplyContext;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentThreadDeletion;
import com.nowcoder.community.content.domain.model.CommentTransitionStatus;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class CommentApplicationServiceTest {

    private ContentSanitizer sensitiveFilter;
    private IdempotencyGuard idempotencyGuard;
    private UserModerationGuard moderationGuard;
    private CommentRepository commentRepository;
    private PostContentRepository postContentPort;
    private PostCounterCache postCounterCache;
    private CommentPageCache commentPageCache;
    private SocialBlockQueryApi blockQueryApi;
    private CommentDomainEventPublisher domainEventPublisher;
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
        blockQueryApi = mock(SocialBlockQueryApi.class);
        domainEventPublisher = mock(CommentDomainEventPublisher.class);
        service = new CommentApplicationService(
                sensitiveFilter,
                idempotencyGuard,
                new SpringHtmlContentTextCodec(),
                moderationGuard,
                new CommentDomainService(),
                commentRepository,
                postContentPort,
                postCounterCache,
                commentPageCache,
                blockQueryApi,
                domainEventPublisher
        );
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
        when(blockQueryApi.isEitherBlocked(userId, postAuthorId)).thenReturn(false);
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
                blockQueryApi,
                commentRepository,
                postCounterCache,
                domainEventPublisher
        );
        inOrder.verify(moderationGuard).assertCanSpeak(userId);
        inOrder.verify(postContentPort).getById(postId);
        inOrder.verify(blockQueryApi).isEitherBlocked(userId, postAuthorId);
        ArgumentCaptor<CommentDraft> draftCaptor = ArgumentCaptor.forClass(CommentDraft.class);
        inOrder.verify(commentRepository).create(draftCaptor.capture());
        inOrder.verify(postContentPort).incrementCommentCount(postId, 1);
        inOrder.verify(postCounterCache).incrementCommentCount(postId, 1L);
        ArgumentCaptor<CommentCreatedDomainEvent> eventCaptor = ArgumentCaptor.forClass(CommentCreatedDomainEvent.class);
        inOrder.verify(domainEventPublisher).commentCreated(eventCaptor.capture());

        CommentDraft draft = draftCaptor.getValue();
        assertThat(draft.userId()).isEqualTo(userId);
        assertThat(draft.postId()).isEqualTo(postId);
        assertThat(draft.rootCommentId()).isNull();
        assertThat(draft.parentCommentId()).isNull();
        assertThat(draft.replyToUserId()).isNull();
        assertThat(draft.content()).isEqualTo("clean &amp; body");
        assertThat(draft.createTime()).isNotNull();

        CommentCreatedDomainEvent event = eventCaptor.getValue();
        assertThat(event.commentId()).isEqualTo(commentId);
        assertThat(event.postId()).isEqualTo(postId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.entityType()).isEqualTo(EntityTypes.POST);
        assertThat(event.entityId()).isEqualTo(postId);
        assertThat(event.targetUserId()).isEqualTo(postAuthorId);
        assertThat(event.content()).isEqualTo("clean & body");
        assertThat(event.createTime()).isEqualTo(draft.createTime().toInstant());
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
        when(blockQueryApi.isEitherBlocked(userId, postAuthorId)).thenReturn(false);
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
    void updateShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.update(null))
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
        when(blockQueryApi.isEitherBlocked(userId, postAuthorId)).thenReturn(false);
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
                postCounterCache,
                commentPageCache,
                blockQueryApi,
                domainEventPublisher
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
        when(blockQueryApi.isEitherBlocked(userId, postAuthorId)).thenReturn(false);
        when(sensitiveFilter.filter("body")).thenReturn("body");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        CommentCreateResult first = service.create("idem-replay-comment", command);
        CommentCreateResult replay = service.create("idem-replay-comment", command);

        assertThat(first.commentId()).isEqualTo(commentId);
        assertThat(replay.commentId()).isEqualTo(commentId);
        verify(commentRepository, times(1)).create(any(CommentDraft.class));
        verify(postContentPort, times(1)).incrementCommentCount(postId, 1);
        verify(postCounterCache, times(1)).incrementCommentCount(postId, 1L);
        verify(domainEventPublisher, times(1)).commentCreated(any(CommentCreatedDomainEvent.class));
    }

    @Test
    void createShouldRejectSameIdempotencyKeyWithDifferentCommentFingerprint() {
        useRealIdempotencyGuard(new InMemoryIdempotencyStore());
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID commentId = uuid(200);

        when(postContentPort.getById(postId)).thenReturn(post(postId, postAuthorId));
        when(blockQueryApi.isEitherBlocked(userId, postAuthorId)).thenReturn(false);
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
        verify(domainEventPublisher, times(1)).commentCreated(any(CommentCreatedDomainEvent.class));
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
        when(blockQueryApi.isEitherBlocked(userId, rootAuthorId)).thenReturn(false);
        when(sensitiveFilter.filter("reply")).thenReturn("reply");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        service.create(
                "idem-reply-target",
                new CreateCommentCommand(userId, postId, rootCommentId, "reply")
        );

        var order = inOrder(commentRepository, blockQueryApi, sensitiveFilter);
        order.verify(commentRepository).lockReplyContext(postId, rootCommentId);
        order.verify(blockQueryApi).isEitherBlocked(userId, rootAuthorId);
        order.verify(sensitiveFilter).filter("reply");
        ArgumentCaptor<CommentDraft> draftCaptor = ArgumentCaptor.forClass(CommentDraft.class);
        verify(commentRepository).create(draftCaptor.capture());
        assertThat(draftCaptor.getValue().postId()).isEqualTo(postId);
        assertThat(draftCaptor.getValue().rootCommentId()).isEqualTo(rootCommentId);
        assertThat(draftCaptor.getValue().parentCommentId()).isEqualTo(rootCommentId);
        assertThat(draftCaptor.getValue().replyToUserId()).isEqualTo(rootAuthorId);
        ArgumentCaptor<CommentCreatedDomainEvent> eventCaptor = ArgumentCaptor.forClass(CommentCreatedDomainEvent.class);
        verify(domainEventPublisher).commentCreated(eventCaptor.capture());
        assertThat(eventCaptor.getValue().entityType()).isEqualTo(EntityTypes.COMMENT);
        assertThat(eventCaptor.getValue().entityId()).isEqualTo(rootCommentId);
        assertThat(eventCaptor.getValue().targetUserId()).isEqualTo(rootAuthorId);
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
        when(blockQueryApi.isEitherBlocked(userId, directParentAuthorId)).thenReturn(false);
        when(sensitiveFilter.filter("reply")).thenReturn("reply");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        service.create("idem-parent-reply", new CreateCommentCommand(userId, postId, directParentId, "reply"));

        verify(blockQueryApi).isEitherBlocked(userId, directParentAuthorId);
        verify(commentRepository).create(org.mockito.ArgumentMatchers.argThat(draft ->
                postId.equals(draft.postId())
                        && rootCommentId.equals(draft.rootCommentId())
                        && directParentId.equals(draft.parentCommentId())
                        && directParentAuthorId.equals(draft.replyToUserId())));
        ArgumentCaptor<CommentCreatedDomainEvent> event = ArgumentCaptor.forClass(CommentCreatedDomainEvent.class);
        verify(domainEventPublisher).commentCreated(event.capture());
        assertThat(event.getValue().entityType()).isEqualTo(EntityTypes.COMMENT);
        assertThat(event.getValue().entityId()).isEqualTo(directParentId);
        assertThat(event.getValue().targetUserId()).isEqualTo(directParentAuthorId);
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

        verify(blockQueryApi, never()).isEitherBlocked(any(), any());
        verify(sensitiveFilter, never()).filter(anyString());
        verify(commentRepository, never()).create(any(CommentDraft.class));
        verify(postContentPort, never()).incrementCommentCount(any(UUID.class), any(Integer.class));
        verify(domainEventPublisher, never()).commentCreated(any(CommentCreatedDomainEvent.class));
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
        when(blockQueryApi.isEitherBlocked(userId, postAuthorId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                "idem-3",
                new CreateCommentCommand(userId, postId, null, "blocked")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        verify(commentRepository, never()).create(any(CommentDraft.class));
        verify(postContentPort, never()).incrementCommentCount(any(UUID.class), any(Integer.class));
        verify(domainEventPublisher, never()).commentCreated(any(CommentCreatedDomainEvent.class));
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

        service.update(new UpdateCommentCommand(userId, postId, commentId, " hello & world "));

        var inOrder = inOrder(moderationGuard, postContentPort, commentRepository);
        inOrder.verify(moderationGuard).assertCanSpeak(userId);
        inOrder.verify(postContentPort).getById(postId);
        inOrder.verify(commentRepository).getRequiredSnapshot(commentId);
        ArgumentCaptor<CommentEdit> edit = ArgumentCaptor.forClass(CommentEdit.class);
        inOrder.verify(commentRepository).apply(edit.capture());
        assertThat(edit.getValue().commentId()).isEqualTo(commentId);
        assertThat(edit.getValue().expectedVersion()).isEqualTo(existing.version());
        assertThat(edit.getValue().content()).isEqualTo("clean");
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
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        when(commentRepository.getActiveThreadSnapshots(commentId)).thenReturn(affected);
        when(commentRepository.apply(any(CommentThreadDeletion.class)))
                .thenReturn(CommentDeletionResult.applied(affected));

        service.deleteByAuthor(userId, postId, commentId);

        var inOrder = inOrder(commentRepository, postContentPort, postCounterCache);
        inOrder.verify(commentRepository).getRequiredSnapshot(commentId);
        inOrder.verify(commentRepository).getActiveThreadSnapshots(commentId);
        inOrder.verify(commentRepository).apply(any(CommentThreadDeletion.class));
        inOrder.verify(postContentPort).incrementCommentCount(postId, -3);
        inOrder.verify(postCounterCache).incrementCommentCount(postId, -3L);
    }

    @Test
    void deleteByAuthorShouldPublishDeleteEventForEveryActuallyDeletedComment() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        UUID replyId = uuid(201);
        UUID nestedReplyId = uuid(202);
        CommentSnapshot existing = rootComment(commentId, userId, postId);
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        UUID replyAuthorId = uuid(2);
        UUID nestedReplyAuthorId = uuid(3);
        List<CommentSnapshot> affected = List.of(
                rootComment(commentId, userId, postId),
                replyComment(replyId, replyAuthorId, postId, commentId, userId),
                replyComment(nestedReplyId, nestedReplyAuthorId, postId, commentId, replyAuthorId)
        );
        when(commentRepository.getActiveThreadSnapshots(commentId)).thenReturn(affected);
        when(commentRepository.apply(any(CommentThreadDeletion.class)))
                .thenReturn(CommentDeletionResult.applied(affected));

        service.deleteByAuthor(userId, postId, commentId);

        ArgumentCaptor<CommentDeletedDomainEvent> eventCaptor = ArgumentCaptor.forClass(CommentDeletedDomainEvent.class);
        verify(domainEventPublisher, times(3)).commentDeleted(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(CommentDeletedDomainEvent::commentId)
                .containsExactly(commentId, replyId, nestedReplyId);
        assertThat(eventCaptor.getAllValues()).extracting(CommentDeletedDomainEvent::postId)
                .containsExactly(postId, postId, postId);
        assertThat(eventCaptor.getAllValues()).extracting(CommentDeletedDomainEvent::userId)
                .containsExactly(userId, replyAuthorId, nestedReplyAuthorId);
        assertThat(eventCaptor.getAllValues()).extracting(CommentDeletedDomainEvent::entityType)
                .containsExactly(EntityTypes.POST, EntityTypes.COMMENT, EntityTypes.COMMENT);
        assertThat(eventCaptor.getAllValues()).extracting(CommentDeletedDomainEvent::entityId)
                .containsExactly(postId, commentId, commentId);
        assertThat(eventCaptor.getAllValues()).allSatisfy(event -> assertThat(event.createTime()).isNotNull());
    }

    @Test
    void deleteByAuthorShouldRejectNestedReplyWhenRoutePostDoesNotMatchRootPost() {
        UUID userId = uuid(1);
        UUID actualPostId = uuid(100);
        UUID routePostId = uuid(101);
        UUID parentCommentId = uuid(200);
        UUID replyId = uuid(201);
        CommentSnapshot reply = replyComment(replyId, userId, actualPostId, parentCommentId, uuid(2));
        when(commentRepository.getRequiredSnapshot(replyId)).thenReturn(reply);

        assertThatThrownBy(() -> service.deleteByAuthor(userId, routePostId, replyId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));

        verify(commentRepository, never()).apply(any(CommentDeletion.class));
        verify(commentRepository, never()).apply(any(CommentThreadDeletion.class));
        verify(postContentPort, never()).incrementCommentCount(any(UUID.class), any(Integer.class));
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
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        when(commentRepository.getActiveThreadSnapshots(commentId)).thenReturn(activeThread);
        when(commentRepository.apply(any(CommentThreadDeletion.class)))
                .thenReturn(CommentDeletionResult.applied(activeThread));

        service.deleteByAuthor(userId, postId, commentId);

        verify(postContentPort).incrementCommentCount(postId, -2);
        ArgumentCaptor<CommentDeletedDomainEvent> events = ArgumentCaptor.forClass(CommentDeletedDomainEvent.class);
        verify(domainEventPublisher, times(2)).commentDeleted(events.capture());
        assertThat(events.getAllValues())
                .extracting(CommentDeletedDomainEvent::commentId)
                .containsExactly(commentId, nestedReplyId)
                .doesNotContain(replyId);
    }

    @Test
    void deleteByAuthorShouldSkipSideEffectsWhenNoCommentsChanged() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        CommentSnapshot existing = rootComment(commentId, userId, postId);
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        when(commentRepository.getActiveThreadSnapshots(commentId)).thenReturn(List.of(existing));
        when(commentRepository.apply(any(CommentThreadDeletion.class))).thenReturn(CommentDeletionResult.noOp());

        service.deleteByAuthor(userId, postId, commentId);

        verify(postContentPort, never()).incrementCommentCount(any(UUID.class), any(Integer.class));
        verify(domainEventPublisher, never()).commentDeleted(any());
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
                postCounterCache,
                commentPageCache,
                blockQueryApi,
                domainEventPublisher
        );
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
