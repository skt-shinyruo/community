package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

public class CommentDomainService {

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

    public record CreateTarget(
            UUID postId,
            UUID rootCommentId,
            UUID parentCommentId,
            UUID replyToUserId,
            UUID targetUserId
    ) {
    }
}
