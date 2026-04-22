package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.action.CommentActionApi;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CommentActionService implements CommentActionApi {

    private final CommentService commentService;
    private final IdempotencyGuard idempotencyGuard;

    public CommentActionService(CommentService commentService, IdempotencyGuard idempotencyGuard) {
        this.commentService = commentService;
        this.idempotencyGuard = idempotencyGuard;
    }

    @Override
    public UUID addComment(UUID userId, String idempotencyKey, UUID postId, Integer entityType, UUID entityId, UUID targetId, String content) {
        return idempotencyGuard.executeRequired(
                "content:create_comment",
                userId,
                idempotencyKey,
                UUID.class,
                () -> commentService.addComment(userId, postId, entityType, entityId, targetId, content)
        );
    }

    @Override
    public void updateComment(UUID userId, UUID postId, UUID commentId, String content) {
        commentService.updateComment(userId, postId, commentId, content);
    }
}
