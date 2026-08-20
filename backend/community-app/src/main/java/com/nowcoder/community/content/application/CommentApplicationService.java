package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.RequestFingerprint;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentEdit;
import com.nowcoder.community.content.domain.model.CommentReplyContext;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentTransitionStatus;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.social.api.action.SocialInteractionActionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

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
    private final ContentReadModelsAfterCommit readModelsAfterCommit;
    private final SocialInteractionActionApi interactionActionApi;
    private final ContentEventPublisher eventPublisher;
    private final CommentDeletionTransactionOperations deletionOperations;
    private final Clock clock;

    public CommentApplicationService(
            ContentSanitizer sensitiveFilter,
            IdempotencyGuard idempotencyGuard,
            ContentTextCodec textCodec,
            UserModerationGuard moderationGuard,
            CommentDomainService domainService,
            CommentRepository commentRepository,
            PostContentRepository postContentPort,
            ContentReadModelsAfterCommit readModelsAfterCommit,
            SocialInteractionActionApi interactionActionApi,
            ContentEventPublisher eventPublisher,
            CommentDeletionTransactionOperations deletionOperations,
            Clock clock
    ) {
        this.sensitiveFilter = Objects.requireNonNull(sensitiveFilter, "sensitiveFilter");
        this.idempotencyGuard = Objects.requireNonNull(idempotencyGuard, "idempotencyGuard");
        this.textCodec = Objects.requireNonNull(textCodec, "textCodec");
        this.moderationGuard = Objects.requireNonNull(moderationGuard, "moderationGuard");
        this.domainService = Objects.requireNonNull(domainService, "domainService");
        this.commentRepository = Objects.requireNonNull(commentRepository, "commentRepository");
        this.postContentPort = Objects.requireNonNull(postContentPort, "postContentPort");
        this.readModelsAfterCommit = Objects.requireNonNull(readModelsAfterCommit, "readModelsAfterCommit");
        this.interactionActionApi = Objects.requireNonNull(interactionActionApi, "interactionActionApi");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.deletionOperations = Objects.requireNonNull(deletionOperations, "deletionOperations");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public CommentCreateResult create(String idempotencyKey, CreateCommentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return createFromCommand(idempotencyKey, command);
    }

    @Transactional
    public void updateComment(UUID userId, UUID postId, UUID commentId, String content) {
        updateCommentInternal(userId, postId, commentId, content);
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
                () -> {
                    CommentMutationResult created = createInsideTransaction(command);
                    readModelsAfterCommit.commentCreated(postId, created.postAggregateVersion());
                    return created.commentId();
                }
        );
        return new CommentCreateResult(commentId);
    }

    private void updateCommentInternal(UUID userId, UUID postId, UUID commentId, String content) {
        if (userId == null || postId == null || commentId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId/commentId 非法");
        }

        moderationGuard.assertCanSpeak(userId);
        postContentPort.getById(postId);
        CommentSnapshot existing = commentRepository.getRequiredSnapshot(commentId);
        Date now = Date.from(clock.instant());
        CommentEdit edit = Comment.reconstitute(existing)
                .editByAuthor(userId, postId, sanitize(content), now);
        CommentTransitionStatus status = commentRepository.apply(edit);
        switch (status) {
            case APPLIED -> {
                long postAggregateVersion = mutateActivePost(postId, 0);
                readModelsAfterCommit.commentEdited(postId, postAggregateVersion);
            }
            case NO_OP, NOT_FOUND -> throw new BusinessException(ContentErrorCode.COMMENT_NOT_FOUND);
            case STALE -> throw staleTransition();
        }
    }

    public void deleteByAuthor(UUID userId, UUID postId, UUID commentId) {
        deleteWithBoundedOperations(userId, postId, commentId, false, "author_delete");
    }

    public void deleteByModeration(UUID actorUserId, UUID commentId, String deletedReason) {
        deleteWithBoundedOperations(actorUserId, null, commentId, true, deletedReason);
    }

    private void deleteWithBoundedOperations(
            UUID actorUserId,
            UUID requestedPostId,
            UUID commentId,
            boolean moderator,
            String deletedReason
    ) {
        CommentSnapshot existing = commentRepository.findSnapshot(commentId)
                .orElseThrow(() -> new BusinessException(ContentErrorCode.COMMENT_NOT_FOUND));
        UUID postId = resolvePostId(existing);
        if (requestedPostId != null && !requestedPostId.equals(postId)) {
            throw new BusinessException(INVALID_ARGUMENT, "commentId 不属于该帖子");
        }
        if (existing.status() == 0) {
            Comment aggregate = Comment.reconstitute(existing);
            CommentDeletion deletion = moderator
                    ? aggregate.deleteByModerator(actorUserId, deletedReason, Date.from(clock.instant()))
                    : aggregate.deleteByAuthor(actorUserId, postId, deletedReason, Date.from(clock.instant()));
            CommentDeletionResult rootResult = existing.rootComment()
                    ? deletionOperations.deleteRoot(deletion, postId)
                    : deletionOperations.deleteSingle(deletion, postId);
            if (rootResult == null) {
                throw new IllegalStateException("comment deletion returned no result");
            }
            switch (rootResult.status()) {
                case STALE -> throw staleTransition();
                case NOT_FOUND -> throw new BusinessException(ContentErrorCode.COMMENT_NOT_FOUND);
                case APPLIED, NO_OP -> {
                    if (!existing.rootComment() || moderator) {
                        return;
                    }
                }
            }
            deleteReplyBatches(existing.rootCommentId(), postId, deletion.deletedBy(),
                    deletion.deletedReason(), deletion.deletedTime());
            return;
        }

        // A previous request may have committed the root tombstone before a
        // later reply batch failed. Retrying the same command resumes cleanup.
        if (existing.rootComment() && !moderator) {
            deleteReplyBatches(existing.rootCommentId(), postId,
                    existing.deletedBy() == null ? actorUserId : existing.deletedBy(),
                    StringUtils.hasText(existing.deletedReason())
                            ? existing.deletedReason() : deletedReason,
                    existing.deletedTime() == null ? Date.from(clock.instant()) : existing.deletedTime());
        }
    }

    private void deleteReplyBatches(
            UUID rootCommentId,
            UUID postId,
            UUID deletedBy,
            String deletedReason,
            Date deletedTime
    ) {
        while (true) {
            CommentDeletionResult batch = deletionOperations.deleteReplyBatch(
                    rootCommentId,
                    postId,
                    deletedBy,
                    deletedReason,
                    deletedTime,
                    CommentDeletionTransactionOperations.REPLY_BATCH_SIZE
            );
            if (batch == null || !batch.changed()) {
                return;
            }
        }
    }

    private CommentMutationResult createInsideTransaction(CreateCommentCommand command) {
        UUID userId = command.userId();
        UUID postId = command.postId();

        moderationGuard.assertCanSpeak(userId);
        DiscussPost post = postContentPort.getById(postId);
        CommentReplyContext context = command.parentCommentId() == null
                ? null
                : commentRepository.lockReplyContext(postId, command.parentCommentId())
                        .orElseThrow(() -> new BusinessException(NOT_FOUND, "资源不存在"));
        CommentDomainService.CreateTarget target = domainService.resolveCreateTarget(
                postId,
                post.getUserId(),
                context
        );
        if (target.targetUserId() != null) {
            interactionActionApi.assertInteractionAllowed(userId, target.targetUserId());
        }

        String safeContent = sanitize(command.content());
        Date createTime = Date.from(clock.instant());
        CommentDraft draft = domainService.createDraft(
                userId,
                target.postId(),
                target.rootCommentId(),
                target.parentCommentId(),
                target.replyToUserId(),
                safeContent,
                createTime
        );
        long postAggregateVersion = mutateActivePost(postId, 1);
        UUID commentId = commentRepository.create(draft);

        String decodedContent = textCodec.decodeOnRead(safeContent);
        var createdAt = createTime.toInstant();
        CommentPayload payload = new CommentPayload(
                commentId,
                postId,
                userId,
                target.parentCommentId() == null ? EntityTypes.POST : EntityTypes.COMMENT,
                target.parentCommentId() == null ? postId : target.parentCommentId(),
                target.targetUserId(),
                decodedContent,
                createdAt,
                postAggregateVersion
        );
        eventPublisher.publishCommentCreated(payload);
        return new CommentMutationResult(commentId, postAggregateVersion);
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

    private long mutateActivePost(UUID postId, int commentCountDelta) {
        long aggregateVersion = postContentPort.incrementActiveCommentCount(postId, commentCountDelta);
        if (aggregateVersion <= 0L) {
            throw new BusinessException(ContentErrorCode.POST_NOT_FOUND);
        }
        return aggregateVersion;
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

    public record CommentCreateResult(UUID commentId) {
    }

    public record CreateCommentCommand(
            UUID userId,
            UUID postId,
            UUID parentCommentId,
            String content
    ) {
    }

    private record CommentMutationResult(UUID commentId, long postAggregateVersion) {
    }
}
