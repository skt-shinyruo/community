package com.nowcoder.community.content.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.IdempotencyProperties;
import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.content.application.command.CreateCommentCommand;
import com.nowcoder.community.content.application.command.UpdateCommentCommand;
import com.nowcoder.community.content.application.port.ContentSanitizer;
import com.nowcoder.community.content.application.port.PostContentPort;
import com.nowcoder.community.content.application.result.CommentCreateResult;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.growth.api.model.GrowthCommentTaskProgressRequest;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.action.UserPointsAwardActionApi;
import com.nowcoder.community.user.api.model.UserCommentPointsAwardRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
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
import static org.mockito.Mockito.when;

class CommentApplicationServiceTest {

    private ContentSanitizer sensitiveFilter;
    private IdempotencyGuard idempotencyGuard;
    private UserModerationGuard moderationGuard;
    private CommentRepository commentRepository;
    private PostContentPort postContentPort;
    private SocialBlockQueryApi blockQueryApi;
    private UserPointsAwardActionApi pointsAwardService;
    private GrowthTaskProgressActionApi taskProgressTriggerService;
    private CommentDomainEventPublisher domainEventPublisher;
    private PostWriteSideEffectScheduler postWriteSideEffectScheduler;
    private PlatformTransactionManager transactionManager;
    private CommentApplicationService service;

    @BeforeEach
    void setUp() {
        sensitiveFilter = mock(ContentSanitizer.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        moderationGuard = mock(UserModerationGuard.class);
        commentRepository = mock(CommentRepository.class);
        postContentPort = mock(PostContentPort.class);
        blockQueryApi = mock(SocialBlockQueryApi.class);
        pointsAwardService = mock(UserPointsAwardActionApi.class);
        taskProgressTriggerService = mock(GrowthTaskProgressActionApi.class);
        domainEventPublisher = mock(CommentDomainEventPublisher.class);
        postWriteSideEffectScheduler = mock(PostWriteSideEffectScheduler.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        service = new CommentApplicationService(
                sensitiveFilter,
                idempotencyGuard,
                new ContentTextCodec(new ContentRenderProperties()),
                moderationGuard,
                new CommentDomainService(),
                commentRepository,
                postContentPort,
                blockQueryApi,
                pointsAwardService,
                taskProgressTriggerService,
                domainEventPublisher,
                postWriteSideEffectScheduler,
                transactionManager
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
                pointsAwardService,
                taskProgressTriggerService,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );
        inOrder.verify(moderationGuard).assertCanSpeak(userId);
        inOrder.verify(postContentPort).getById(postId);
        inOrder.verify(blockQueryApi).isEitherBlocked(userId, postAuthorId);
        ArgumentCaptor<CommentDraft> draftCaptor = ArgumentCaptor.forClass(CommentDraft.class);
        inOrder.verify(commentRepository).create(draftCaptor.capture());
        inOrder.verify(postContentPort).incrementCommentCount(postId, 1);
        ArgumentCaptor<UserCommentPointsAwardRequest> pointsRequestCaptor = ArgumentCaptor.forClass(UserCommentPointsAwardRequest.class);
        inOrder.verify(pointsAwardService).awardCommentCreated(pointsRequestCaptor.capture());
        ArgumentCaptor<GrowthCommentTaskProgressRequest> growthRequestCaptor = ArgumentCaptor.forClass(GrowthCommentTaskProgressRequest.class);
        inOrder.verify(taskProgressTriggerService).triggerCommentCreated(growthRequestCaptor.capture());
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

        UserCommentPointsAwardRequest pointsRequest = pointsRequestCaptor.getValue();
        assertThat(pointsRequest.commentId()).isEqualTo(commentId);
        assertThat(pointsRequest.userId()).isEqualTo(userId);

        GrowthCommentTaskProgressRequest growthRequest = growthRequestCaptor.getValue();
        assertThat(growthRequest.commentId()).isEqualTo(commentId);
        assertThat(growthRequest.userId()).isEqualTo(userId);
        assertThat(growthRequest.createTime()).isEqualTo(draft.createTime().toInstant());

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

        UUID returned = service.addComment(userId, "idem-legacy", postId, EntityTypes.POST, null, null, "hi");

        assertThat(returned).isEqualTo(commentId);
        verify(idempotencyGuard).executeRequired(
                eq("content:create_comment"),
                eq(userId),
                eq("idem-legacy"),
                eq(UUID.class),
                any()
        );
    }

    @Test
    void createShouldCommitCommentTransactionBeforeSavingUuidIdempotencySuccess() {
        UUID userId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID commentId = uuid(200);
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyGuard realGuard = new IdempotencyGuard(new ObjectMapper(), store, null, new IdempotencyProperties());
        PlatformTransactionManager realTransactionManager = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus transactionStatus = new SimpleTransactionStatus();
        when(realTransactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(store.tryAcquireProcessing(eq("content:create_comment"), eq(userId), eq("idem-transaction"), any(Duration.class)))
                .thenReturn(true);
        when(postContentPort.getById(postId)).thenReturn(post(postId, postAuthorId));
        when(blockQueryApi.isEitherBlocked(userId, postAuthorId)).thenReturn(false);
        when(sensitiveFilter.filter("hi")).thenReturn("hi");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);
        service = new CommentApplicationService(
                sensitiveFilter,
                realGuard,
                new ContentTextCodec(new ContentRenderProperties()),
                moderationGuard,
                new CommentDomainService(),
                commentRepository,
                postContentPort,
                blockQueryApi,
                pointsAwardService,
                taskProgressTriggerService,
                domainEventPublisher,
                postWriteSideEffectScheduler,
                realTransactionManager
        );

        CommentCreateResult result = service.create(
                "idem-transaction",
                new CreateCommentCommand(userId, postId, EntityTypes.POST, null, null, "hi")
        );

        assertThat(result.commentId()).isEqualTo(commentId);
        var inOrder = inOrder(commentRepository, realTransactionManager, store);
        inOrder.verify(realTransactionManager).getTransaction(any(TransactionDefinition.class));
        inOrder.verify(commentRepository).create(any(CommentDraft.class));
        inOrder.verify(realTransactionManager).commit(transactionStatus);
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
        verify(pointsAwardService, never()).awardCommentCreated(any(UserCommentPointsAwardRequest.class));
        verify(taskProgressTriggerService, never()).triggerCommentCreated(any(GrowthCommentTaskProgressRequest.class));
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
        verify(pointsAwardService, never()).awardCommentCreated(any(UserCommentPointsAwardRequest.class));
        verify(taskProgressTriggerService, never()).triggerCommentCreated(any(GrowthCommentTaskProgressRequest.class));
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
