package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentEdit;
import com.nowcoder.community.content.domain.model.CommentReplyContext;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentThreadDeletion;
import com.nowcoder.community.content.domain.model.CommentTransitionStatus;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentTransitionTargetDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;

@Repository
public class MyBatisCommentRepository implements CommentRepository {

    private final CommentMapper commentMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MyBatisCommentRepository(CommentMapper commentMapper) {
        this(commentMapper, new UuidV7Generator());
    }

    MyBatisCommentRepository(CommentMapper commentMapper, UuidV7Generator idGenerator) {
        this.commentMapper = commentMapper;
        this.idGenerator = idGenerator;
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
    public List<CommentSnapshot> getActiveThreadSnapshots(UUID rootCommentId) {
        if (rootCommentId == null) {
            return List.of();
        }
        lockRootBeforeThread(rootCommentId);
        List<CommentDataObject> rows = orderedRows(
                rootCommentId,
                commentMapper.selectThreadForUpdate(rootCommentId)
        );
        CommentDataObject root = rows.stream()
                .filter(row -> rootCommentId.equals(row.getId()))
                .findFirst()
                .orElse(null);
        if (root == null || root.getStatus() != 0 || root.getParentCommentId() != null) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> row.getStatus() == 0)
                .map(CommentPersistenceConverter::toSnapshot)
                .sorted(threadOrder(rootCommentId))
                .toList();
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
    public CommentDeletionResult apply(CommentThreadDeletion deletion) {
        lockRootBeforeThread(deletion.rootCommentId());
        List<CommentDataObject> rows = orderedRows(
                deletion.rootCommentId(),
                commentMapper.selectThreadForUpdate(deletion.rootCommentId())
        );
        Map<UUID, CommentDataObject> rowsById = new LinkedHashMap<>();
        for (CommentDataObject row : rows) {
            rowsById.put(row.getId(), row);
        }
        CommentDataObject root = rowsById.get(deletion.rootCommentId());
        if (root == null) {
            return CommentDeletionResult.notFound();
        }
        if (root.getStatus() != 0) {
            return CommentDeletionResult.noOp();
        }

        List<UUID> activeIds = rows.stream()
                .filter(row -> row.getStatus() == 0)
                .map(CommentDataObject::getId)
                .toList();
        List<UUID> targetIds = deletion.targets().stream()
                .map(CommentThreadDeletion.Target::commentId)
                .toList();
        if (!activeIds.equals(targetIds)) {
            return CommentDeletionResult.stale();
        }

        for (CommentThreadDeletion.Target target : deletion.targets()) {
            CommentDataObject row = rowsById.get(target.commentId());
            if (row == null) {
                return CommentDeletionResult.notFound();
            }
            if (row.getStatus() != 0
                    || row.getVersion() != target.expectedVersion()
                    || !deletion.rootCommentId().equals(row.getRootCommentId())) {
                return CommentDeletionResult.stale();
            }
        }

        List<CommentTransitionTargetDataObject> persistenceTargets = deletion.targets().stream()
                .map(target -> new CommentTransitionTargetDataObject(
                        target.commentId(),
                        target.expectedVersion()
                ))
                .toList();
        int updated = commentMapper.applyThreadDeletion(
                deletion.rootCommentId(),
                persistenceTargets,
                deletion.deletedBy(),
                deletion.deletedReason(),
                deletion.deletedTime()
        );
        ensureAppliedCount(deletion.targets().size(), updated);
        List<CommentSnapshot> affected = deletion.targets().stream()
                .map(target -> CommentPersistenceConverter.toSnapshot(rowsById.get(target.commentId())))
                .toList();
        return CommentDeletionResult.applied(affected);
    }

    private void lockRootBeforeThread(UUID rootCommentId) {
        // Keep thread deletion on the same root-first lock order as nested reply creation.
        commentMapper.selectByIdForUpdate(rootCommentId);
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

    private static List<CommentDataObject> orderedRows(UUID rootCommentId, List<CommentDataObject> rows) {
        return safeRows(rows).stream()
                .sorted(Comparator
                        .comparing((CommentDataObject row) -> !rootCommentId.equals(row.getId()))
                        .thenComparing(
                                CommentDataObject::getCreateTime,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(row -> row.getId().toString()))
                .toList();
    }

    private static Comparator<CommentSnapshot> threadOrder(UUID rootCommentId) {
        return Comparator
                .comparing((CommentSnapshot snapshot) -> !rootCommentId.equals(snapshot.id()))
                .thenComparing(CommentSnapshot::createTime)
                .thenComparing(snapshot -> snapshot.id().toString());
    }

    private static void ensureAppliedCount(int expected, int actual) {
        if (actual != expected) {
            throw new IllegalStateException(
                    "comment transition cardinality mismatch: expected=" + expected + ", actual=" + actual
            );
        }
    }
}
