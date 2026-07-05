package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.content.application.command.CreateCommentCommand;
import com.nowcoder.community.content.application.command.UpdateCommentCommand;
import com.nowcoder.community.content.application.ContentSanitizer;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.application.result.CommentCreateResult;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDeletedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.infra.idempotency.RequestFingerprint;
import com.nowcoder.community.social.api.action.SocialLikeCleanupActionApi;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PostContentRepository postContentPort;
    private final SocialBlockQueryApi blockQueryApi;
    private final SocialLikeCleanupActionApi socialLikeCleanupActionApi;
    private final CommentDomainEventPublisher domainEventPublisher;
    private final PostWriteSideEffectScheduler postWriteSideEffectScheduler;

    public CommentApplicationService(
            ContentSanitizer sensitiveFilter,
            IdempotencyGuard idempotencyGuard,
            ContentTextCodec textCodec,
            UserModerationGuard moderationGuard,
            CommentDomainService domainService,
            CommentRepository commentRepository,
            PostContentRepository postContentPort,
            SocialBlockQueryApi blockQueryApi,
            SocialLikeCleanupActionApi socialLikeCleanupActionApi,
            CommentDomainEventPublisher domainEventPublisher,
            PostWriteSideEffectScheduler postWriteSideEffectScheduler
    ) {
        this.sensitiveFilter = sensitiveFilter;
        this.idempotencyGuard = idempotencyGuard;
        this.textCodec = textCodec;
        this.moderationGuard = moderationGuard;
        this.domainService = domainService;
        this.commentRepository = commentRepository;
        this.postContentPort = postContentPort;
        this.blockQueryApi = blockQueryApi;
        this.socialLikeCleanupActionApi = socialLikeCleanupActionApi;
        this.domainEventPublisher = domainEventPublisher;
        this.postWriteSideEffectScheduler = postWriteSideEffectScheduler;
    }

    @Transactional
    public CommentCreateResult create(
            UUID userId,
            String idempotencyKey,
            UUID postId,
            Integer entityType,
            UUID entityId,
            UUID targetId,
            String content
    ) {
        return createFromCommand(idempotencyKey, new CreateCommentCommand(userId, postId, entityType, entityId, targetId, content));
    }

    @Transactional
    public CommentCreateResult create(String idempotencyKey, CreateCommentCommand command) {
        return createFromCommand(idempotencyKey, command);
    }

    @Transactional
    public UUID addComment(UUID userId, String idempotencyKey, UUID postId, Integer entityType, UUID entityId, UUID targetId, String content) {
        return createFromCommand(idempotencyKey, new CreateCommentCommand(userId, postId, entityType, entityId, targetId, content)).commentId();
    }

    @Transactional
    public void updateComment(UUID userId, UUID postId, UUID commentId, String content) {
        updateFromCommand(new UpdateCommentCommand(userId, postId, commentId, content));
    }

    @Transactional
    public void update(UpdateCommentCommand command) {
        updateFromCommand(command);
    }

    private CommentCreateResult createFromCommand(String idempotencyKey, CreateCommentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        UUID userId = command.userId();
        UUID postId = command.postId();
        if (userId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        String requestHash = createCommentRequestHash(command);
        UUID commentId = idempotencyGuard.executeRequired(
                CREATE_COMMENT_IDEMPOTENCY_SCOPE,
                userId,
                idempotencyKey,
                requestHash,
                ContentErrorCode.REQUEST_REPLAY_CONFLICT,
                UUID.class,
                () -> createInsideTransaction(command)
        );
        return new CommentCreateResult(commentId);
    }

    private void updateFromCommand(UpdateCommentCommand command) {
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

    @Transactional
    public void deleteByAuthor(UUID userId, UUID postId, UUID commentId) {
        CommentSnapshot existing = commentRepository.getRequiredSnapshot(commentId);
        UUID actualPostId = resolvePostId(existing);
        domainService.assertDeletableByAuthor(existing, userId, postId, actualPostId);
        deleteActiveThread(existing, actualPostId, userId, "author_delete");
    }

    @Transactional
    public void deleteByModeration(UUID actorUserId, UUID commentId, String deletedReason) {
        CommentSnapshot existing = commentRepository.getRequiredSnapshot(commentId);
        deleteActiveThread(existing, resolvePostId(existing), actorUserId, deletedReason);
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
        var createdAt = createTime.toInstant();
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

        domainEventPublisher.commentCreated(event);
        postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
        return commentId;
    }

    private String createCommentRequestHash(CreateCommentCommand command) {
        String canonical = "content:create_comment"
                + "|postId=" + canonicalValue(command.postId())
                + "|entityType=" + canonicalValue(command.entityType())
                + "|entityId=" + canonicalValue(command.entityId())
                + "|targetId=" + canonicalValue(command.targetId())
                + "|content=" + canonicalValue(command.content());
        return RequestFingerprint.sha256(canonical);
    }

    private String canonicalValue(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }

    private void deleteActiveThread(CommentSnapshot existing, UUID postId, UUID deletedBy, String deletedReason) {
        Date deletedTime = new Date();
        CommentDeletionResult deletion = commentRepository.markActiveThreadDeleted(
                existing.id(),
                deletedBy,
                deletedReason,
                deletedTime
        );
        if (!deletion.changed()) {
            return;
        }
        postContentPort.incrementCommentCount(postId, -deletion.deletedCount());
        AfterCommitExecutor.runAfterCommit(() -> {
            for (UUID deletedCommentId : deletion.deletedCommentIds()) {
                try {
                    socialLikeCleanupActionApi.cleanupEntityLikes(EntityTypes.COMMENT, deletedCommentId);
                } catch (RuntimeException ignored) {
                    // best-effort after-commit cleanup; event/outbox path remains the source for downstream repair
                }
            }
        });
        for (CommentSnapshot deletedComment : deletion.deletedComments()) {
            domainEventPublisher.commentDeleted(new CommentDeletedDomainEvent(
                    deletedComment.id(),
                    postId,
                    deletedComment.userId(),
                    deletedComment.entityType(),
                    deletedComment.entityId(),
                    deletedTime.toInstant()
            ));
        }
        postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
    }

    private UUID resolvePostId(CommentSnapshot comment) {
        CommentSnapshot current = comment;
        for (int i = 0; i < 12; i++) {
            if (current.entityType() == EntityTypes.POST) {
                return current.entityId();
            }
            if (current.entityType() != EntityTypes.COMMENT || current.entityId() == null) {
                throw new BusinessException(INVALID_ARGUMENT, "评论归属帖子非法");
            }
            current = commentRepository.getRequiredSnapshot(current.entityId());
        }
        throw new BusinessException(INVALID_ARGUMENT, "评论层级非法");
    }

    private String sanitize(String content) {
        String safe = textCodec.escapeOnWrite(content == null ? "" : content.trim());
        return sensitiveFilter.filter(safe);
    }
}
