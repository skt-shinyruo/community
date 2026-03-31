package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.action.CommentActionApi;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import org.springframework.stereotype.Service;

@Service
public class CommentActionService implements CommentActionApi {

    private final CommentService commentService;
    private final IdempotencyGuard idempotencyGuard;

    public CommentActionService(CommentService commentService, IdempotencyGuard idempotencyGuard) {
        this.commentService = commentService;
        this.idempotencyGuard = idempotencyGuard;
    }

    @Override
    public int addComment(int userId, String idempotencyKey, int postId, Integer entityType, Integer entityId, Integer targetId, String content) {
        return idempotencyGuard.executeRequired(
                "content:create_comment",
                userId,
                idempotencyKey,
                Integer.class,
                () -> commentService.addComment(userId, postId, entityType, entityId, targetId, content)
        );
    }

    @Override
    public void updateComment(int userId, int postId, int commentId, String content) {
        commentService.updateComment(userId, postId, commentId, content);
    }
}
