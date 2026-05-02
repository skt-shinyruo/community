package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
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
        Comment comment = new Comment();
        comment.setId(idGenerator.next());
        comment.setUserId(draft.userId());
        comment.setEntityType(draft.entityType());
        comment.setEntityId(draft.entityId());
        comment.setTargetId(draft.targetId());
        comment.setContent(draft.content());
        comment.setStatus(0);
        comment.setCreateTime(draft.createTime());
        commentMapper.insertComment(comment);
        return comment.getId();
    }

    @Override
    public CommentSnapshot getRequiredSnapshot(UUID commentId) {
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null || comment.getStatus() != 0) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        return toSnapshot(comment);
    }

    @Override
    public Optional<CommentSnapshot> findSnapshot(UUID commentId) {
        if (commentId == null) {
            return Optional.empty();
        }
        Comment comment = commentMapper.selectCommentById(commentId);
        return comment == null ? Optional.empty() : Optional.of(toSnapshot(comment));
    }

    @Override
    public Optional<CommentSnapshot> findActiveSnapshot(UUID commentId) {
        if (commentId == null) {
            return Optional.empty();
        }
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null || comment.getStatus() != 0) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(comment));
    }

    @Override
    public void updateContent(UUID commentId, String content, Date updateTime) {
        int updated = commentMapper.updateCommentContent(commentId, content, updateTime);
        if (updated <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "更新评论失败");
        }
    }

    @Override
    public CommentDeletionResult markActiveThreadDeleted(UUID commentId, UUID deletedBy, String deletedReason, Date deletedTime) {
        Comment root = commentMapper.selectCommentById(commentId);
        if (root == null || root.getStatus() != 0) {
            return new CommentDeletionResult(List.of());
        }
        List<UUID> candidateIds = new ArrayList<>();
        List<CommentSnapshot> deletedComments = new ArrayList<>();
        Queue<UUID> queue = new ArrayDeque<>();
        candidateIds.add(commentId);
        queue.add(commentId);
        while (!queue.isEmpty()) {
            UUID parentId = queue.remove();
            List<UUID> replyIds = commentMapper.selectActiveReplyIds(parentId);
            if (replyIds == null || replyIds.isEmpty()) {
                continue;
            }
            candidateIds.addAll(replyIds);
            queue.addAll(replyIds);
        }
        for (UUID candidateId : candidateIds) {
            Comment comment = commentMapper.selectCommentById(candidateId);
            if (comment == null || comment.getStatus() != 0) {
                continue;
            }
            if (commentMapper.updateActiveCommentDeleted(candidateId, deletedBy, deletedReason, deletedTime) > 0) {
                deletedComments.add(toSnapshot(comment));
            }
        }
        return new CommentDeletionResult(deletedComments);
    }

    private static CommentSnapshot toSnapshot(Comment comment) {
        return new CommentSnapshot(
                comment.getId(),
                comment.getUserId(),
                comment.getEntityType(),
                comment.getEntityId(),
                comment.getTargetId(),
                comment.getContent(),
                comment.getStatus(),
                comment.getCreateTime(),
                comment.getUpdateTime(),
                comment.getEditCount()
        );
    }
}
