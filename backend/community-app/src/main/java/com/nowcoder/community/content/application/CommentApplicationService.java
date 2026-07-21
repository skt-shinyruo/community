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
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentEdit;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentThreadDeletion;
import com.nowcoder.community.content.domain.model.CommentTransitionStatus;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.common.idempotency.RequestFingerprint;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
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
    private final PostCounterCache postCounterCache;
    private final CommentPageCache commentPageCache;
    private final SocialBlockQueryApi blockQueryApi;
    private final CommentDomainEventPublisher domainEventPublisher;

    public CommentApplicationService(
            ContentSanitizer sensitiveFilter,
            IdempotencyGuard idempotencyGuard,
            ContentTextCodec textCodec,
            UserModerationGuard moderationGuard,
            CommentDomainService domainService,
            CommentRepository commentRepository,
            PostContentRepository postContentPort,
            PostCounterCache postCounterCache,
            CommentPageCache commentPageCache,
            SocialBlockQueryApi blockQueryApi,
            CommentDomainEventPublisher domainEventPublisher
    ) {
        this.sensitiveFilter = sensitiveFilter;
        this.idempotencyGuard = idempotencyGuard;
        this.textCodec = textCodec;
        this.moderationGuard = moderationGuard;
        this.domainService = domainService;
        this.commentRepository = commentRepository;
        this.postContentPort = postContentPort;
        this.postCounterCache = postCounterCache;
        this.commentPageCache = commentPageCache;
        this.blockQueryApi = blockQueryApi;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public CommentCreateResult create(String idempotencyKey, CreateCommentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return createFromCommand(idempotencyKey, command);
    }

    @Transactional
    public void updateComment(UUID userId, UUID postId, UUID commentId, String content) {
        updateFromCommand(new UpdateCommentCommand(userId, postId, commentId, content));
    }

    @Transactional
    public void update(UpdateCommentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        updateFromCommand(command);
    }

    private CommentCreateResult createFromCommand(String idempotencyKey, CreateCommentCommand command) {
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
        evictCommentPageCacheAfterCommit(postId);
        return new CommentCreateResult(commentId);
    }

    private void updateFromCommand(UpdateCommentCommand command) {
        UUID userId = command.userId();
        UUID postId = command.postId();
        UUID commentId = command.commentId();
        if (userId == null || postId == null || commentId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId/commentId 非法");
        }

        moderationGuard.assertCanSpeak(userId);
        postContentPort.getById(postId);
        CommentSnapshot existing = commentRepository.getRequiredSnapshot(commentId);
        Date now = new Date();
        CommentEdit edit = Comment.reconstitute(existing)
                .editByAuthor(userId, postId, sanitize(command.content()), now);
        CommentTransitionStatus status = commentRepository.apply(edit);
        switch (status) {
            case APPLIED -> evictCommentPageCacheAfterCommit(postId);
            case NO_OP, NOT_FOUND -> throw new BusinessException(ContentErrorCode.COMMENT_NOT_FOUND);
            case STALE -> throw staleTransition();
        }
    }

    @Transactional
    public void deleteByAuthor(UUID userId, UUID postId, UUID commentId) {
        CommentSnapshot existing = commentRepository.getRequiredSnapshot(commentId);
        UUID actualPostId = resolvePostId(existing);
        Comment aggregate = Comment.reconstitute(existing);
        CommentDeletion deletion = aggregate.deleteByAuthor(
                userId,
                postId,
                "author_delete",
                new Date()
        );
        deleteActiveThread(aggregate, deletion, actualPostId);
    }

    @Transactional
    public void deleteByModeration(UUID actorUserId, UUID commentId, String deletedReason) {
        CommentSnapshot existing = commentRepository.getRequiredSnapshot(commentId);
        Comment aggregate = Comment.reconstitute(existing);
        CommentDeletion deletion = aggregate.deleteByModerator(actorUserId, deletedReason, new Date());
        deleteActiveThread(aggregate, deletion, resolvePostId(existing));
    }

    private UUID createInsideTransaction(CreateCommentCommand command) {
        UUID userId = command.userId();
        UUID postId = command.postId();

        moderationGuard.assertCanSpeak(userId);
        DiscussPost post = postContentPort.getById(postId);
        CommentSnapshot parentComment = command.parentCommentId() != null
                ? commentRepository.findActiveSnapshot(command.parentCommentId()).orElse(null)
                : null;
        CommentDomainService.CreateTarget target = domainService.resolveCreateTarget(
                postId,
                command.parentCommentId(),
                null,
                post.getUserId(),
                parentComment
        );
        if (target.targetUserId() != null && blockQueryApi.isEitherBlocked(userId, target.targetUserId())) {
            throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
        }

        String safeContent = sanitize(command.content());
        Date createTime = new Date();
        CommentDraft draft = domainService.createDraft(
                userId,
                target.postId(),
                target.rootCommentId(),
                target.parentCommentId(),
                target.replyToUserId(),
                safeContent,
                createTime
        );
        UUID commentId = commentRepository.create(draft);
        postContentPort.incrementCommentCount(postId, 1);
        postCounterCache.incrementCommentCount(postId, 1L);

        String decodedContent = textCodec.decodeOnRead(safeContent);
        var createdAt = createTime.toInstant();
        CommentCreatedDomainEvent event = new CommentCreatedDomainEvent(
                commentId,
                postId,
                userId,
                target.parentCommentId() == null ? EntityTypes.POST : EntityTypes.COMMENT,
                target.parentCommentId() == null ? postId : target.parentCommentId(),
                target.targetUserId(),
                decodedContent,
                createdAt
        );

        domainEventPublisher.commentCreated(event);
        return commentId;
    }

    private String createCommentRequestHash(CreateCommentCommand command) {
        String canonical = "content:create_comment"
                + "|postId=" + canonicalValue(command.postId())
                + "|parentCommentId=" + canonicalValue(command.parentCommentId())
                + "|content=" + canonicalValue(command.content());
        return RequestFingerprint.sha256(canonical);
    }

    private String canonicalValue(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }

    private void deleteActiveThread(Comment aggregate, CommentDeletion transition, UUID postId) {
        CommentDeletionResult result;
        if (aggregate.isRootComment()) {
            List<CommentSnapshot> activeThread = commentRepository.getActiveThreadSnapshots(transition.commentId());
            if (activeThread.isEmpty()) {
                return;
            }
            result = commentRepository.apply(CommentThreadDeletion.from(transition, activeThread));
        } else {
            result = commentRepository.apply(transition);
        }

        switch (result.status()) {
            case NO_OP -> {
                return;
            }
            case STALE -> throw staleTransition();
            case NOT_FOUND -> throw new BusinessException(ContentErrorCode.COMMENT_NOT_FOUND);
            case APPLIED -> {
            }
        }

        postContentPort.incrementCommentCount(postId, -result.deletedCount());
        postCounterCache.incrementCommentCount(postId, -result.deletedCount());
        evictCommentPageCacheAfterCommit(postId);
        for (CommentSnapshot deletedComment : result.deletedComments()) {
            domainEventPublisher.commentDeleted(new CommentDeletedDomainEvent(
                    deletedComment.id(),
                    postId,
                    deletedComment.userId(),
                    deletedComment.rootComment() ? EntityTypes.POST : EntityTypes.COMMENT,
                    deletedComment.rootComment() ? postId : deletedComment.parentCommentId(),
                    transition.deletedTime().toInstant()
            ));
        }
    }

    private static IllegalStateException staleTransition() {
        return new IllegalStateException("comment transition stale");
    }

    private UUID resolvePostId(CommentSnapshot comment) {
        if (comment == null || comment.postId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "评论归属帖子非法");
        }
        return comment.postId();
    }

    private String sanitize(String content) {
        String safe = textCodec.escapeOnWrite(content == null ? "" : content.trim());
        return sensitiveFilter.filter(safe);
    }

    private void evictCommentPageCacheAfterCommit(UUID postId) {
        AfterCommitExecutor.runAfterCommit(() -> commentPageCache.evictPost(postId));
    }
}
