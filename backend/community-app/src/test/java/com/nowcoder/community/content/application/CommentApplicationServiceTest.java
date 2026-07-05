package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.IdempotencyProperties;
import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.application.command.CreateCommentCommand;
import com.nowcoder.community.content.application.command.UpdateCommentCommand;
import com.nowcoder.community.content.infrastructure.text.SpringHtmlContentTextCodec;
import com.nowcoder.community.content.application.ContentSanitizer;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.application.result.CommentCreateResult;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDeletedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.social.api.action.SocialLikeCleanupActionApi;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Date;
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
    private SocialBlockQueryApi blockQueryApi;
    private SocialLikeCleanupActionApi socialLikeCleanupActionApi;
    private CommentDomainEventPublisher domainEventPublisher;
    private PostWriteSideEffectScheduler postWriteSideEffectScheduler;
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
        blockQueryApi = mock(SocialBlockQueryApi.class);
        socialLikeCleanupActionApi = mock(SocialLikeCleanupActionApi.class);
        domainEventPublisher = mock(CommentDomainEventPublisher.class);
        postWriteSideEffectScheduler = mock(PostWriteSideEffectScheduler.class);
        service = new CommentApplicationService(
                sensitiveFilter,
                idempotencyGuard,
                new SpringHtmlContentTextCodec(),
                moderationGuard,
                new CommentDomainService(),
                commentRepository,
                postContentPort,
                blockQueryApi,
                socialLikeCleanupActionApi,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );
    }

    @Test
    void createPostCommentShouldOwnWriteOrchestration() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID commentId = uuid(200);
        DiscussPost post = post(postId, postAuthorId);

        when(idempotencyGuard.executeRequired(eq("content:create_comment"), eq(userId), anyString(), eq(UUID.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(4).get());
        when(postContentPort.getById(postId)).thenReturn(post);
        when(blockQueryApi.isEitherBlocked(userId, postAuthorId)).thenReturn(false);
        when(sensitiveFilter.filter("hello &amp; world")).thenReturn("clean &amp; body");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        CommentCreateResult result = service.create(
                "idem-1",
                new CreateCommentCommand(userId, postId, EntityTypes.POST, null, uuid(999), " hello & world ")
        );

        assertThat(result.commentId()).isEqualTo(commentId);
        verify(idempotencyGuard).executeRequired(
                eq("content:create_comment"),
                eq(userId),
                eq("idem-1"),
                eq(UUID.class),
                any()
        );

        var inOrder = inOrder(
                moderationGuard,
                postContentPort,
                blockQueryApi,
                commentRepository,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );
        inOrder.verify(moderationGuard).assertCanSpeak(userId);
        inOrder.verify(postContentPort).getById(postId);
        inOrder.verify(blockQueryApi).isEitherBlocked(userId, postAuthorId);
        ArgumentCaptor<CommentDraft> draftCaptor = ArgumentCaptor.forClass(CommentDraft.class);
        inOrder.verify(commentRepository).create(draftCaptor.capture());
        inOrder.verify(postContentPort).incrementCommentCount(postId, 1);
        ArgumentCaptor<CommentCreatedDomainEvent> eventCaptor = ArgumentCaptor.forClass(CommentCreatedDomainEvent.class);
        inOrder.verify(domainEventPublisher).commentCreated(eventCaptor.capture());
        inOrder.verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(postId);

        CommentDraft draft = draftCaptor.getValue();
        assertThat(draft.userId()).isEqualTo(userId);
        assertThat(draft.entityType()).isEqualTo(EntityTypes.POST);
        assertThat(draft.entityId()).isEqualTo(postId);
        assertThat(draft.targetId()).isNull();
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
    void commentApplicationServiceShouldNotDependOnRewardOrGrowthSideEffectApis() {
        assertThat(Arrays.stream(CommentApplicationService.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .doesNotContain(
                        "com.nowcoder.community.user.api.action.UserRewardActionApi",
                        "com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi"
                );
    }

    @Test
    void addCommentShouldDelegateThroughCreatePath() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID commentId = uuid(200);

        when(idempotencyGuard.executeRequired(eq("content:create_comment"), eq(userId), anyString(), eq(UUID.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(4).get());
        when(postContentPort.getById(postId)).thenReturn(post(postId, postAuthorId));
        when(blockQueryApi.isEitherBlocked(userId, postAuthorId)).thenReturn(false);
        when(sensitiveFilter.filter("hi")).thenReturn("hi");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        UUID returned = service.addComment(userId, "idem-comment-1", postId, EntityTypes.POST, null, null, "hi");

        assertThat(returned).isEqualTo(commentId);
        verify(idempotencyGuard).executeRequired(
                eq("content:create_comment"),
                eq(userId),
                eq("idem-comment-1"),
                eq(UUID.class),
                any()
        );
    }

    @Test
    void createShouldSaveUuidIdempotencySuccessPayloadUsingCreatedCommentId() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID commentId = uuid(200);
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyGuard realGuard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());
        when(store.tryAcquireProcessing(eq("content:create_comment"), eq(userId), eq("idem-transaction"), any(Duration.class)))
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
                blockQueryApi,
                socialLikeCleanupActionApi,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );

        CommentCreateResult result = service.create(
                "idem-transaction",
                new CreateCommentCommand(userId, postId, EntityTypes.POST, null, null, "hi")
        );

        assertThat(result.commentId()).isEqualTo(commentId);
        var inOrder = inOrder(commentRepository, store);
        inOrder.verify(commentRepository).create(any(CommentDraft.class));
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        inOrder.verify(store).saveSuccess(
                eq("content:create_comment"),
                eq(userId),
                eq("idem-transaction"),
                jsonCaptor.capture(),
                any(Duration.class)
        );
        assertThat(jsonCaptor.getValue()).isEqualTo("\"" + commentId + "\"");
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
    void createReplyShouldPersistAuthoritativeTargetUserInsteadOfRawClientTargetId() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID targetCommentId = uuid(200);
        UUID targetCommentAuthorId = uuid(201);
        UUID rawTargetId = uuid(999);
        UUID commentId = uuid(300);
        CommentSnapshot targetComment = activeComment(targetCommentId, targetCommentAuthorId, EntityTypes.POST, postId);

        when(idempotencyGuard.executeRequired(eq("content:create_comment"), eq(userId), anyString(), eq(UUID.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(4).get());
        when(postContentPort.getById(postId)).thenReturn(post(postId, uuid(2)));
        when(commentRepository.findActiveSnapshot(targetCommentId)).thenReturn(Optional.of(targetComment));
        when(blockQueryApi.isEitherBlocked(userId, targetCommentAuthorId)).thenReturn(false);
        when(sensitiveFilter.filter("reply")).thenReturn("reply");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        service.create(
                "idem-reply-target",
                new CreateCommentCommand(userId, postId, EntityTypes.COMMENT, targetCommentId, rawTargetId, "reply")
        );

        ArgumentCaptor<CommentDraft> draftCaptor = ArgumentCaptor.forClass(CommentDraft.class);
        verify(commentRepository).create(draftCaptor.capture());
        assertThat(draftCaptor.getValue().targetId()).isEqualTo(targetCommentAuthorId);
        ArgumentCaptor<CommentCreatedDomainEvent> eventCaptor = ArgumentCaptor.forClass(CommentCreatedDomainEvent.class);
        verify(domainEventPublisher).commentCreated(eventCaptor.capture());
        assertThat(eventCaptor.getValue().targetUserId()).isEqualTo(targetCommentAuthorId);
    }

    @Test
    void createReplyShouldRejectCrossPostTargetBeforePersistence() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID otherPostId = uuid(101);
        UUID postAuthorId = uuid(2);
        UUID targetCommentId = uuid(200);
        CommentSnapshot targetComment = activeComment(targetCommentId, postAuthorId, EntityTypes.POST, otherPostId);

        when(idempotencyGuard.executeRequired(eq("content:create_comment"), eq(userId), anyString(), eq(UUID.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(4).get());
        when(postContentPort.getById(postId)).thenReturn(post(postId, postAuthorId));
        when(commentRepository.findActiveSnapshot(targetCommentId)).thenReturn(Optional.of(targetComment));

        assertThatThrownBy(() -> service.create(
                "idem-2",
                new CreateCommentCommand(userId, postId, EntityTypes.COMMENT, targetCommentId, null, "reply")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));

        verify(commentRepository, never()).create(any(CommentDraft.class));
        verify(postContentPort, never()).incrementCommentCount(any(UUID.class), any(Integer.class));
        verify(domainEventPublisher, never()).commentCreated(any(CommentCreatedDomainEvent.class));
        verify(postWriteSideEffectScheduler, never()).schedulePostScoreRefresh(any(UUID.class));
    }

    @Test
    void createCommentShouldRejectEitherBlockedBeforePersistence() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);

        when(idempotencyGuard.executeRequired(eq("content:create_comment"), eq(userId), anyString(), eq(UUID.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<UUID>>getArgument(4).get());
        when(postContentPort.getById(postId)).thenReturn(post(postId, postAuthorId));
        when(blockQueryApi.isEitherBlocked(userId, postAuthorId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                "idem-3",
                new CreateCommentCommand(userId, postId, EntityTypes.POST, null, null, "blocked")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        verify(commentRepository, never()).create(any(CommentDraft.class));
        verify(postContentPort, never()).incrementCommentCount(any(UUID.class), any(Integer.class));
        verify(domainEventPublisher, never()).commentCreated(any(CommentCreatedDomainEvent.class));
        verify(postWriteSideEffectScheduler, never()).schedulePostScoreRefresh(any(UUID.class));
    }

    @Test
    void updateCommentShouldSanitizeAndPersistThroughRepository() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        CommentSnapshot existing = activeComment(commentId, userId, EntityTypes.POST, postId);

        when(postContentPort.getById(postId)).thenReturn(post(postId, uuid(2)));
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        when(sensitiveFilter.filter("hello &amp; world")).thenReturn("clean");

        service.update(new UpdateCommentCommand(userId, postId, commentId, " hello & world "));

        var inOrder = inOrder(moderationGuard, postContentPort, commentRepository);
        inOrder.verify(moderationGuard).assertCanSpeak(userId);
        inOrder.verify(postContentPort).getById(postId);
        inOrder.verify(commentRepository).getRequiredSnapshot(commentId);
        inOrder.verify(commentRepository).updateContent(eq(commentId), eq("clean"), any(Date.class));
    }

    @Test
    void deleteByAuthorShouldDeleteActiveCommentThreadAndApplySideEffects() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        UUID replyId = uuid(201);
        UUID nestedReplyId = uuid(202);
        CommentSnapshot existing = activeComment(commentId, userId, EntityTypes.POST, postId);
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        when(commentRepository.markActiveThreadDeleted(eq(commentId), eq(userId), eq("author_delete"), any(Date.class)))
                .thenReturn(new CommentDeletionResult(List.of(
                        activeComment(commentId, userId, EntityTypes.POST, postId),
                        activeComment(replyId, userId, EntityTypes.COMMENT, commentId),
                        activeComment(nestedReplyId, userId, EntityTypes.COMMENT, replyId)
                )));

        service.deleteByAuthor(userId, postId, commentId);

        var inOrder = inOrder(commentRepository, postContentPort, socialLikeCleanupActionApi, postWriteSideEffectScheduler);
        inOrder.verify(commentRepository).getRequiredSnapshot(commentId);
        inOrder.verify(commentRepository).markActiveThreadDeleted(eq(commentId), eq(userId), eq("author_delete"), any(Date.class));
        inOrder.verify(postContentPort).incrementCommentCount(postId, -3);
        inOrder.verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.COMMENT, commentId);
        inOrder.verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.COMMENT, replyId);
        inOrder.verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.COMMENT, nestedReplyId);
        inOrder.verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(postId);
    }

    @Test
    void deleteByAuthorShouldPublishDeleteEventForEveryActuallyDeletedComment() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        UUID replyId = uuid(201);
        UUID nestedReplyId = uuid(202);
        CommentSnapshot existing = activeComment(commentId, userId, EntityTypes.POST, postId);
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        UUID replyAuthorId = uuid(2);
        UUID nestedReplyAuthorId = uuid(3);
        when(commentRepository.markActiveThreadDeleted(eq(commentId), eq(userId), eq("author_delete"), any(Date.class)))
                .thenReturn(new CommentDeletionResult(List.of(
                        activeComment(commentId, userId, EntityTypes.POST, postId),
                        activeComment(replyId, replyAuthorId, EntityTypes.COMMENT, commentId),
                        activeComment(nestedReplyId, nestedReplyAuthorId, EntityTypes.COMMENT, replyId)
                )));

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
                .containsExactly(postId, commentId, replyId);
        assertThat(eventCaptor.getAllValues()).allSatisfy(event -> assertThat(event.createTime()).isNotNull());
    }

    @Test
    void deleteByAuthorShouldRejectNestedReplyWhenRoutePostDoesNotMatchRootPost() {
        UUID userId = uuid(1);
        UUID actualPostId = uuid(100);
        UUID routePostId = uuid(101);
        UUID parentCommentId = uuid(200);
        UUID replyId = uuid(201);
        CommentSnapshot parent = activeComment(parentCommentId, uuid(2), EntityTypes.POST, actualPostId);
        CommentSnapshot reply = activeComment(replyId, userId, EntityTypes.COMMENT, parentCommentId);
        when(commentRepository.getRequiredSnapshot(replyId)).thenReturn(reply);
        when(commentRepository.getRequiredSnapshot(parentCommentId)).thenReturn(parent);
        when(commentRepository.markActiveThreadDeleted(eq(replyId), eq(userId), eq("author_delete"), any(Date.class)))
                .thenReturn(new CommentDeletionResult(List.of(reply)));

        assertThatThrownBy(() -> service.deleteByAuthor(userId, routePostId, replyId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));

        verify(commentRepository, never()).markActiveThreadDeleted(any(UUID.class), any(UUID.class), anyString(), any(Date.class));
        verify(postContentPort, never()).incrementCommentCount(any(UUID.class), any(Integer.class));
        verify(socialLikeCleanupActionApi, never()).cleanupEntityLikes(any(Integer.class), any(UUID.class));
    }

    @Test
    void deleteByAuthorShouldRunSocialCleanupAfterCommitWhenTransactionActive() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        CommentSnapshot existing = activeComment(commentId, userId, EntityTypes.POST, postId);
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        when(commentRepository.markActiveThreadDeleted(eq(commentId), eq(userId), eq("author_delete"), any(Date.class)))
                .thenReturn(new CommentDeletionResult(List.of(existing)));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.deleteByAuthor(userId, postId, commentId);

            verify(socialLikeCleanupActionApi, never()).cleanupEntityLikes(any(Integer.class), any(UUID.class));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.COMMENT, commentId);
    }

    @Test
    void deleteByAuthorShouldContinueCleanupWhenOneAfterCommitTaskFails() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        UUID replyId = uuid(201);
        CommentSnapshot existing = activeComment(commentId, userId, EntityTypes.POST, postId);
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        when(commentRepository.markActiveThreadDeleted(eq(commentId), eq(userId), eq("author_delete"), any(Date.class)))
                .thenReturn(new CommentDeletionResult(List.of(
                        activeComment(commentId, userId, EntityTypes.POST, postId),
                        activeComment(replyId, userId, EntityTypes.COMMENT, commentId)
                )));
        org.mockito.Mockito.doThrow(new RuntimeException("cleanup failed"))
                .when(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.COMMENT, commentId);

        service.deleteByAuthor(userId, postId, commentId);

        verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.COMMENT, commentId);
        verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.COMMENT, replyId);
    }

    @Test
    void deleteByAuthorShouldUseActuallyDeletedCommentsForCountAndCleanup() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        UUID replyId = uuid(201);
        UUID nestedReplyId = uuid(202);
        CommentSnapshot existing = activeComment(commentId, userId, EntityTypes.POST, postId);
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        when(commentRepository.markActiveThreadDeleted(eq(commentId), eq(userId), eq("author_delete"), any(Date.class)))
                .thenReturn(new CommentDeletionResult(List.of(
                        activeComment(commentId, userId, EntityTypes.POST, postId),
                        activeComment(nestedReplyId, userId, EntityTypes.COMMENT, replyId)
                )));

        service.deleteByAuthor(userId, postId, commentId);

        verify(postContentPort).incrementCommentCount(postId, -2);
        verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.COMMENT, commentId);
        verify(socialLikeCleanupActionApi).cleanupEntityLikes(EntityTypes.COMMENT, nestedReplyId);
        verify(socialLikeCleanupActionApi, never()).cleanupEntityLikes(EntityTypes.COMMENT, replyId);
        verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(postId);
    }

    @Test
    void deleteByAuthorShouldSkipSideEffectsWhenNoCommentsChanged() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        CommentSnapshot existing = activeComment(commentId, userId, EntityTypes.POST, postId);
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(existing);
        when(commentRepository.markActiveThreadDeleted(eq(commentId), eq(userId), eq("author_delete"), any(Date.class)))
                .thenReturn(new CommentDeletionResult(List.of()));

        service.deleteByAuthor(userId, postId, commentId);

        verify(postContentPort, never()).incrementCommentCount(any(UUID.class), any(Integer.class));
        verify(socialLikeCleanupActionApi, never()).cleanupEntityLikes(any(Integer.class), any(UUID.class));
        verify(domainEventPublisher, never()).commentDeleted(any());
        verify(postWriteSideEffectScheduler, never()).schedulePostScoreRefresh(any(UUID.class));
    }

    private static DiscussPost post(UUID postId, UUID authorId) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(authorId);
        return post;
    }

    private static CommentSnapshot activeComment(UUID commentId, UUID userId, int entityType, UUID entityId) {
        return new CommentSnapshot(
                commentId,
                userId,
                entityType,
                entityId,
                null,
                "content",
                0,
                new Date(),
                null,
                0
        );
    }
}
