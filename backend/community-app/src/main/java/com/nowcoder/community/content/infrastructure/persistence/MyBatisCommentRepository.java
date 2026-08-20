package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentEdit;
import com.nowcoder.community.content.domain.model.CommentReplyContext;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentTransitionStatus;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;

@Repository
public class MyBatisCommentRepository implements CommentRepository {

    private final CommentMapper commentMapper;
    private final UuidV7Generator idGenerator;

    public MyBatisCommentRepository(CommentMapper commentMapper, UuidV7Generator idGenerator) {
        this.commentMapper = Objects.requireNonNull(commentMapper, "commentMapper must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    @Override
    public UUID create(CommentDraft draft) {
        if (draft == null || draft.userId() == null || draft.postId() == null || draft.createTime() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "comment draft 非法");
        }
        UUID commentId = idGenerator.next();
        UUID rootCommentId = draft.rootCommentId() == null ? commentId : draft.rootCommentId();

        CommentDataObject row = new CommentDataObject();
        row.setId(commentId);
        row.setPostId(draft.postId());
        row.setUserId(draft.userId());
        row.setRootCommentId(rootCommentId);
        row.setParentCommentId(draft.parentCommentId());
        row.setReplyToUserId(draft.replyToUserId());
        row.setContent(draft.content());
        row.setStatus(0);
        row.setCreateTime(draft.createTime());
        row.setVersion(0L);
        if (commentMapper.insert(row) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "创建评论失败");
        }
        return commentId;
    }

    @Override
    public CommentSnapshot getRequiredSnapshot(UUID commentId) {
        CommentDataObject row = commentMapper.selectById(commentId);
        if (row == null || row.getStatus() != 0) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        return CommentPersistenceConverter.toSnapshot(row);
    }

    @Override
    public Optional<CommentSnapshot> findSnapshot(UUID commentId) {
        if (commentId == null) {
            return Optional.empty();
        }
        CommentDataObject row = commentMapper.selectById(commentId);
        return row == null ? Optional.empty() : Optional.of(CommentPersistenceConverter.toSnapshot(row));
    }

    @Override
    public Optional<CommentSnapshot> findActiveSnapshot(UUID commentId) {
        if (commentId == null) {
            return Optional.empty();
        }
        CommentDataObject row = commentMapper.selectById(commentId);
        if (row == null || row.getStatus() != 0) {
            return Optional.empty();
        }
        return Optional.of(CommentPersistenceConverter.toSnapshot(row));
    }

    @Override
    public Optional<CommentReplyContext> lockReplyContext(UUID postId, UUID directParentCommentId) {
        if (postId == null || directParentCommentId == null) {
            return Optional.empty();
        }
        CommentDataObject hint = commentMapper.selectById(directParentCommentId);
        if (hint == null || hint.getRootCommentId() == null) {
            return Optional.empty();
        }

        UUID rootCommentId = hint.getRootCommentId();
        CommentDataObject lockedRoot = commentMapper.selectByIdForUpdate(rootCommentId);
        CommentDataObject lockedDirectParent = rootCommentId.equals(directParentCommentId)
                ? lockedRoot
                : commentMapper.selectByIdForUpdate(directParentCommentId);
        if (!validLockedRoot(postId, rootCommentId, lockedRoot)
                || !validLockedDirectParent(
                        postId,
                        rootCommentId,
                        directParentCommentId,
                        lockedDirectParent
                )) {
            return Optional.empty();
        }

        CommentSnapshot root = CommentPersistenceConverter.toSnapshot(lockedRoot);
        CommentSnapshot directParent = rootCommentId.equals(directParentCommentId)
                ? root
                : CommentPersistenceConverter.toSnapshot(lockedDirectParent);
        return Optional.of(new CommentReplyContext(directParent, root));
    }

    @Override
    public List<UUID> findDeletedRootIdsWithActiveReplies(int limit) {
        List<UUID> ids = commentMapper.selectDeletedRootIdsWithActiveReplies(
                Math.min(500, Math.max(1, limit)));
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public boolean hasActiveReplies(UUID rootCommentId) {
        return rootCommentId != null && commentMapper.existsActiveReply(rootCommentId) > 0;
    }

    @Override
    public CommentTransitionStatus apply(CommentEdit edit) {
        CommentDataObject current = commentMapper.selectByIdForUpdate(edit.commentId());
        CommentTransitionStatus currentStatus = classify(current, edit.expectedVersion());
        if (currentStatus != CommentTransitionStatus.APPLIED) {
            return currentStatus;
        }
        int updated = commentMapper.applyEdit(
                edit.commentId(),
                edit.expectedVersion(),
                edit.content(),
                edit.updateTime()
        );
        ensureAppliedCount(1, updated);
        return CommentTransitionStatus.APPLIED;
    }

    @Override
    public CommentDeletionResult apply(CommentDeletion deletion) {
        CommentDataObject current = commentMapper.selectByIdForUpdate(deletion.commentId());
        CommentTransitionStatus currentStatus = classify(current, deletion.expectedVersion());
        if (currentStatus != CommentTransitionStatus.APPLIED) {
            return deletionResult(currentStatus);
        }
        int updated = commentMapper.applyDeletion(
                deletion.commentId(),
                deletion.expectedVersion(),
                deletion.deletedBy(),
                deletion.deletedReason(),
                deletion.deletedTime()
        );
        ensureAppliedCount(1, updated);
        return CommentDeletionResult.applied(List.of(CommentPersistenceConverter.toSnapshot(current)));
    }

    @Override
    public CommentDeletionResult deleteActiveReplyBatch(
            UUID rootCommentId,
            UUID deletedBy,
            String deletedReason,
            java.util.Date deletedTime,
            int limit
    ) {
        if (rootCommentId == null || deletedBy == null || deletedTime == null || limit <= 0) {
            return CommentDeletionResult.noOp();
        }
        List<CommentDataObject> rows = safeRows(commentMapper.selectActiveReplyBatchForUpdate(
                rootCommentId, Math.min(limit, 200)));
        if (rows.isEmpty()) {
            return CommentDeletionResult.noOp();
        }
        List<UUID> ids = rows.stream().map(CommentDataObject::getId).toList();
        int updated = commentMapper.applyReplyBatchDeletion(
                rootCommentId, ids, deletedBy, deletedReason, deletedTime);
        ensureAppliedCount(ids.size(), updated);
        return CommentDeletionResult.applied(rows.stream()
                .map(CommentPersistenceConverter::toSnapshot)
                .toList());
    }

    private static boolean validLockedRoot(
            UUID postId,
            UUID rootCommentId,
            CommentDataObject root
    ) {
        return root != null
                && root.getStatus() == 0
                && rootCommentId.equals(root.getId())
                && rootCommentId.equals(root.getRootCommentId())
                && root.getParentCommentId() == null
                && postId.equals(root.getPostId());
    }

    private static boolean validLockedDirectParent(
            UUID postId,
            UUID rootCommentId,
            UUID directParentCommentId,
            CommentDataObject directParent
    ) {
        if (directParent == null
                || directParent.getStatus() != 0
                || !directParentCommentId.equals(directParent.getId())
                || !rootCommentId.equals(directParent.getRootCommentId())
                || !postId.equals(directParent.getPostId())) {
            return false;
        }
        return rootCommentId.equals(directParentCommentId)
                ? directParent.getParentCommentId() == null
                : directParent.getParentCommentId() != null;
    }

    private static CommentTransitionStatus classify(CommentDataObject current, long expectedVersion) {
        if (current == null) {
            return CommentTransitionStatus.NOT_FOUND;
        }
        if (current.getStatus() != 0) {
            return CommentTransitionStatus.NO_OP;
        }
        return current.getVersion() == expectedVersion
                ? CommentTransitionStatus.APPLIED
                : CommentTransitionStatus.STALE;
    }

    private static CommentDeletionResult deletionResult(CommentTransitionStatus status) {
        return switch (status) {
            case NO_OP -> CommentDeletionResult.noOp();
            case STALE -> CommentDeletionResult.stale();
            case NOT_FOUND -> CommentDeletionResult.notFound();
            case APPLIED -> throw new IllegalArgumentException("APPLIED requires affected comments");
        };
    }

    private static List<CommentDataObject> safeRows(List<CommentDataObject> rows) {
        return rows == null ? List.of() : rows;
    }

    private static void ensureAppliedCount(int expected, int actual) {
        if (actual != expected) {
            throw new IllegalStateException(
                    "comment transition cardinality mismatch: expected=" + expected + ", actual=" + actual
            );
        }
    }
}
