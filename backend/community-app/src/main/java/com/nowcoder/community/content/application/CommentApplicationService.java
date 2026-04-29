package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.content.application.command.CreateCommentCommand;
import com.nowcoder.community.content.application.command.UpdateCommentCommand;
import com.nowcoder.community.content.application.port.ContentSanitizer;
import com.nowcoder.community.content.application.port.PostContentPort;
import com.nowcoder.community.content.application.result.CommentCreateResult;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.action.UserPointsAwardActionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class CommentApplicationService {

    private static final String CREATE_COMMENT_IDEMPOTENCY_SCOPE = "content:create_comment";

    private final ContentSanitizer sensitiveFilter;
    private final IdempotencyGuard idempotencyGuard;
    private final ContentTextCodec textCodec;
    private final UserModerationGuard moderationGuard;
    private final CommentDomainService domainService;
    private final CommentRepository commentRepository;
    private final PostContentPort postContentPort;
    private final SocialBlockQueryApi blockQueryApi;
    private final UserPointsAwardActionApi pointsAwardService;
    private final GrowthTaskProgressActionApi taskProgressTriggerService;
    private final CommentDomainEventPublisher domainEventPublisher;
    private final PostWriteSideEffectScheduler postWriteSideEffectScheduler;
    private final TransactionTemplate commentWriteTransactionTemplate;

    public CommentApplicationService(
            ContentSanitizer sensitiveFilter,
            IdempotencyGuard idempotencyGuard,
            ContentTextCodec textCodec,
            UserModerationGuard moderationGuard,
            CommentDomainService domainService,
            CommentRepository commentRepository,
            PostContentPort postContentPort,
            SocialBlockQueryApi blockQueryApi,
            UserPointsAwardActionApi pointsAwardService,
            GrowthTaskProgressActionApi taskProgressTriggerService,
            CommentDomainEventPublisher domainEventPublisher,
            PostWriteSideEffectScheduler postWriteSideEffectScheduler,
            PlatformTransactionManager transactionManager
    ) {
        this.sensitiveFilter = sensitiveFilter;
        this.idempotencyGuard = idempotencyGuard;
        this.textCodec = textCodec;
        this.moderationGuard = moderationGuard;
        this.domainService = domainService;
        this.commentRepository = commentRepository;
        this.postContentPort = postContentPort;
        this.blockQueryApi = blockQueryApi;
        this.pointsAwardService = pointsAwardService;
        this.taskProgressTriggerService = taskProgressTriggerService;
        this.domainEventPublisher = domainEventPublisher;
        this.postWriteSideEffectScheduler = postWriteSideEffectScheduler;
        this.commentWriteTransactionTemplate = new TransactionTemplate(transactionManager);
        this.commentWriteTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CommentCreateResult create(
            UUID userId,
            String idempotencyKey,
            UUID postId,
            Integer entityType,
            UUID entityId,
            UUID targetId,
            String content
    ) {
        return create(idempotencyKey, new CreateCommentCommand(userId, postId, entityType, entityId, targetId, content));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CommentCreateResult create(String idempotencyKey, CreateCommentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        UUID userId = command.userId();
        UUID postId = command.postId();
        if (userId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        UUID commentId = idempotencyGuard.executeRequired(
                CREATE_COMMENT_IDEMPOTENCY_SCOPE,
                userId,
                idempotencyKey,
                UUID.class,
                () -> commentWriteTransactionTemplate.execute(status -> createInsideTransaction(command))
        );
        return new CommentCreateResult(commentId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UUID addComment(UUID userId, String idempotencyKey, UUID postId, Integer entityType, UUID entityId, UUID targetId, String content) {
        return create(userId, idempotencyKey, postId, entityType, entityId, targetId, content).commentId();
    }

    @Transactional
    public void updateComment(UUID userId, UUID postId, UUID commentId, String content) {
        update(new UpdateCommentCommand(userId, postId, commentId, content));
    }

    @Transactional
    public void update(UpdateCommentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        UUID userId = command.userId();
        UUID postId = command.postId();
        UUID commentId = command.commentId();
        if (userId == null || postId == null || commentId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId/commentId 非法");
        }

        moderationGuard.assertCanSpeak(userId);
        postContentPort.getById(postId);
        CommentSnapshot existing = commentRepository.getRequiredSnapshot(commentId);
        CommentSnapshot parent = existing.entityType() == EntityTypes.COMMENT
                ? commentRepository.findSnapshot(existing.entityId()).orElse(null)
                : null;
        Date now = new Date();
        domainService.assertEditableByAuthor(existing, userId, postId, now, parent);
        commentRepository.updateContent(commentId, sanitize(command.content()), now);
    }

    private UUID createInsideTransaction(CreateCommentCommand command) {
        UUID userId = command.userId();
        UUID postId = command.postId();

        moderationGuard.assertCanSpeak(userId);
        DiscussPost post = postContentPort.getById(postId);
        CommentSnapshot targetComment = command.entityType() != null && command.entityType() == EntityTypes.COMMENT
                ? commentRepository.findActiveSnapshot(command.entityId()).orElse(null)
                : null;
        CommentDomainService.CreateTarget target = domainService.resolveCreateTarget(
                postId,
                command.entityType(),
                command.entityId(),
                command.targetId(),
                post.getUserId(),
                targetComment
        );
        if (target.targetUserId() != null && blockQueryApi.isEitherBlocked(userId, target.targetUserId())) {
            throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
        }

        String safeContent = sanitize(command.content());
        Date createTime = new Date();
        CommentDraft draft = domainService.createDraft(
                userId,
                target.entityType(),
                target.entityId(),
                target.targetId(),
                safeContent,
                createTime
        );
        UUID commentId = commentRepository.create(draft);
        postContentPort.incrementCommentCount(postId, 1);

        String decodedContent = textCodec.decodeOnRead(safeContent);
        Instant createdAt = createTime.toInstant();
        CommentPayload payload = commentPayload(
                commentId,
                postId,
                userId,
                target,
                decodedContent,
                createdAt
        );
        CommentCreatedDomainEvent event = new CommentCreatedDomainEvent(
                commentId,
                postId,
                userId,
                target.entityType(),
                target.entityId(),
                target.targetUserId(),
                decodedContent,
                createdAt
        );

        pointsAwardService.awardCommentCreated(payload);
        taskProgressTriggerService.triggerCommentCreated(payload);
        domainEventPublisher.commentCreated(event);
        postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
        return commentId;
    }

    private String sanitize(String content) {
        String safe = textCodec.escapeOnWrite(content == null ? "" : content.trim());
        return sensitiveFilter.filter(safe);
    }

    private static CommentPayload commentPayload(
            UUID commentId,
            UUID postId,
            UUID userId,
            CommentDomainService.CreateTarget target,
            String content,
            Instant createTime
    ) {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(commentId);
        payload.setPostId(postId);
        payload.setUserId(userId);
        payload.setEntityType(target.entityType());
        payload.setEntityId(target.entityId());
        payload.setTargetUserId(target.targetUserId());
        payload.setContent(content);
        payload.setCreateTime(createTime);
        return payload;
    }
}
