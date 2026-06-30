package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;

public class CommentDomainService {

    private static final long EDIT_WINDOW_MILLIS = 15L * 60 * 1000;

    public CreateTarget resolveCreateTarget(
            UUID postId,
            Integer rawEntityType,
            UUID rawEntityId,
            UUID rawTargetId,
            UUID postAuthorUserId,
            CommentSnapshot targetComment
    ) {
        if (postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "postId 非法");
        }
        int entityType = rawEntityType == null ? EntityTypes.POST : rawEntityType;
        if (entityType == EntityTypes.POST) {
            return new CreateTarget(EntityTypes.POST, postId, null, postAuthorUserId);
        }
        if (entityType != EntityTypes.COMMENT) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        if (rawEntityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "回复评论时 entityId(commentId) 不能为空");
        }
        if (targetComment == null || !targetComment.active()) {
            throw new BusinessException(NOT_FOUND, "资源不存在");
        }
        if (!rawEntityId.equals(targetComment.id())
                || targetComment.entityType() != EntityTypes.POST
                || !postId.equals(targetComment.entityId())) {
            throw new BusinessException(NOT_FOUND, "资源不存在");
        }
        UUID targetUserId = targetComment.userId();
        return new CreateTarget(EntityTypes.COMMENT, rawEntityId, targetUserId, targetUserId);
    }

    public CommentDraft createDraft(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID targetId,
            String content,
            Date createTime
    ) {
        if (actorUserId == null || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/entityId 非法");
        }
        if (entityType != EntityTypes.POST && entityType != EntityTypes.COMMENT) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return new CommentDraft(actorUserId, entityType, entityId, targetId, content, createTime == null ? new Date() : createTime);
    }

    public void assertEditableByAuthor(
            CommentSnapshot comment,
            UUID actorUserId,
            UUID postId,
            Date now,
            CommentSnapshot parentComment
    ) {
        if (actorUserId == null || postId == null || comment == null || comment.id() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId/commentId 非法");
        }
        if (!comment.active()) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        if (!actorUserId.equals(comment.userId())) {
            throw new BusinessException(FORBIDDEN, "只能编辑自己的评论");
        }
        if (comment.createTime() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "评论时间非法");
        }
        Date effectiveNow = now == null ? new Date() : now;
        if (effectiveNow.getTime() - comment.createTime().getTime() > EDIT_WINDOW_MILLIS) {
            throw new BusinessException(FORBIDDEN, "已超过可编辑时间（15min）");
        }
        if (comment.entityType() == EntityTypes.POST) {
            if (!postId.equals(comment.entityId())) {
                throw new BusinessException(INVALID_ARGUMENT, "commentId 不属于该帖子");
            }
            return;
        }
        if (comment.entityType() == EntityTypes.COMMENT) {
            if (parentComment == null
                    || comment.entityId() == null
                    || !comment.entityId().equals(parentComment.id())
                    || parentComment.entityType() != EntityTypes.POST
                    || !postId.equals(parentComment.entityId())) {
                throw new BusinessException(INVALID_ARGUMENT, "commentId 不属于该帖子");
            }
            return;
        }
        throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
    }

    public void assertDeletableByAuthor(CommentSnapshot comment, UUID actorUserId, UUID routePostId, UUID actualPostId) {
        if (actorUserId == null || routePostId == null || actualPostId == null || comment == null || comment.id() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId/commentId 非法");
        }
        if (!comment.active()) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        if (!actorUserId.equals(comment.userId())) {
            throw new BusinessException(FORBIDDEN, "只能删除自己的评论");
        }
        if (routePostId.equals(actualPostId)) {
            return;
        }
        throw new BusinessException(INVALID_ARGUMENT, "commentId 不属于该帖子");
    }

    public record CreateTarget(int entityType, UUID entityId, UUID targetId, UUID targetUserId) {
    }
}
