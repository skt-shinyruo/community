package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentReplyContext;
import com.nowcoder.community.content.domain.model.CommentSnapshot;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

public class CommentDomainService {

    public CreateTarget resolveCreateTarget(
            UUID postId,
            UUID postAuthorUserId,
            CommentReplyContext context
    ) {
        if (postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "postId 非法");
        }
        if (context == null) {
            return new CreateTarget(postId, null, null, null, postAuthorUserId);
        }

        CommentSnapshot directParent = context.directParent();
        CommentSnapshot root = context.root();
        boolean sameComment = directParent.id().equals(root.id());
        boolean validRoot = root.active()
                && postId.equals(root.postId())
                && root.id().equals(root.rootCommentId())
                && root.rootComment();
        boolean validDirectParent = directParent.active()
                && postId.equals(directParent.postId())
                && root.id().equals(directParent.rootCommentId())
                && (sameComment ? directParent.rootComment() && directParent.equals(root) : !directParent.rootComment());
        if (!validRoot || !validDirectParent) {
            throw new BusinessException(NOT_FOUND, "资源不存在");
        }
        return new CreateTarget(
                postId,
                root.id(),
                directParent.id(),
                directParent.userId(),
                directParent.userId()
        );
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
