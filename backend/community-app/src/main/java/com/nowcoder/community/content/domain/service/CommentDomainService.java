package com.nowcoder.community.content.domain.service;

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
            UUID rawParentCommentId,
            UUID rawReplyToUserId,
            UUID postAuthorUserId,
            CommentSnapshot parentComment
    ) {
        if (postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "postId 非法");
        }
        if (rawParentCommentId == null) {
            return new CreateTarget(postId, null, null, null, postAuthorUserId);
        }
        if (parentComment == null || !parentComment.active()) {
            throw new BusinessException(NOT_FOUND, "资源不存在");
        }
        if (!rawParentCommentId.equals(parentComment.id())
                || !postId.equals(parentComment.postId())
                || !parentComment.rootComment()) {
            throw new BusinessException(NOT_FOUND, "资源不存在");
        }
        UUID targetUserId = rawReplyToUserId == null ? parentComment.userId() : rawReplyToUserId;
        return new CreateTarget(postId, parentComment.id(), parentComment.id(), targetUserId, targetUserId);
    }

    public CommentDraft createDraft(
            UUID actorUserId,
            UUID postId,
            UUID rootCommentId,
            UUID parentCommentId,
            UUID replyToUserId,
            String content,
            Date createTime
    ) {
        if (actorUserId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        return new CommentDraft(
                actorUserId,
                postId,
                rootCommentId,
                parentCommentId,
                replyToUserId,
                content,
                createTime == null ? new Date() : createTime
        );
    }

    public void assertEditableByAuthor(
            CommentSnapshot comment,
            UUID actorUserId,
            UUID postId,
            Date now
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
        if (!postId.equals(comment.postId())) {
            throw new BusinessException(INVALID_ARGUMENT, "commentId 不属于该帖子");
        }
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

    public record CreateTarget(
            UUID postId,
            UUID rootCommentId,
            UUID parentCommentId,
            UUID replyToUserId,
            UUID targetUserId
    ) {
    }
}
