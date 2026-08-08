package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Keeps the initial tombstone in the caller's transaction while isolating
 * each bounded reply cleanup page in its own short transaction.
 */
@Component
public class CommentDeletionTransactionOperations {

    public static final int REPLY_BATCH_SIZE = 100;

    private final CommentRepository commentRepository;
    private final PostContentRepository postContentRepository;
    private final CommentCacheAfterCommit commentCacheAfterCommit;
    private final ContentEventPublisher eventPublisher;

    public CommentDeletionTransactionOperations(
            CommentRepository commentRepository,
            PostContentRepository postContentRepository,
            CommentCacheAfterCommit commentCacheAfterCommit,
            ContentEventPublisher eventPublisher
    ) {
        this.commentRepository = commentRepository;
        this.postContentRepository = postContentRepository;
        this.commentCacheAfterCommit = commentCacheAfterCommit;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public CommentDeletionResult deleteRoot(CommentDeletion deletion, UUID postId) {
        CommentDeletionResult result = commentRepository.apply(deletion);
        return applySideEffects(result, postId, deletion.deletedTime());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public CommentDeletionResult deleteSingle(CommentDeletion deletion, UUID postId) {
        CommentDeletionResult result = commentRepository.apply(deletion);
        return applySideEffects(result, postId, deletion.deletedTime());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CommentDeletionResult deleteReplyBatch(
            UUID rootCommentId,
            UUID postId,
            UUID deletedBy,
            String deletedReason,
            Date deletedTime,
            int limit
    ) {
        CommentDeletionResult result = commentRepository.deleteActiveReplyBatch(
                rootCommentId,
                deletedBy,
                deletedReason,
                deletedTime,
                Math.min(Math.max(1, limit), REPLY_BATCH_SIZE)
        );
        return applySideEffects(result, postId, deletedTime);
    }

    private CommentDeletionResult applySideEffects(
            CommentDeletionResult result,
            UUID postId,
            Date deletedTime
    ) {
        if (result == null || !result.changed()) {
            return result == null ? CommentDeletionResult.noOp() : result;
        }
        long postAggregateVersion = postContentRepository.incrementActiveCommentCount(
                postId, -result.deletedCount());
        if (postAggregateVersion <= 0L) {
            throw new IllegalStateException("post disappeared while deleting comments");
        }
        Instant occurredAt = deletedTime == null ? Instant.now() : deletedTime.toInstant();
        for (CommentSnapshot deleted : result.deletedComments()) {
            CommentPayload payload = new CommentPayload();
            payload.setCommentId(deleted.id());
            payload.setPostId(postId);
            payload.setUserId(deleted.userId());
            payload.setEntityType(deleted.rootComment() ? EntityTypes.POST : EntityTypes.COMMENT);
            payload.setEntityId(deleted.rootComment() ? postId : deleted.parentCommentId());
            payload.setCreateTime(occurredAt);
            payload.setPostAggregateVersion(postAggregateVersion);
            eventPublisher.publishCommentDeleted(payload);
        }
        commentCacheAfterCommit.incrementCommentCount(postId, -result.deletedCount());
        commentCacheAfterCommit.evictCommentPages(postId);
        commentCacheAfterCommit.evictPostReadModels(postId, postAggregateVersion);
        return result;
    }

}
