package com.nowcoder.community.content.application;

import com.nowcoder.community.content.api.action.CommentActionApi;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.content.application.port.CommentContentPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CommentApplicationService implements CommentActionApi {

    private final CommentContentPort commentContentPort;
    private final IdempotencyGuard idempotencyGuard;

    public CommentApplicationService(CommentContentPort commentContentPort, IdempotencyGuard idempotencyGuard) {
        this.commentContentPort = commentContentPort;
        this.idempotencyGuard = idempotencyGuard;
    }

    @Override
    public UUID addComment(UUID userId, String idempotencyKey, UUID postId, Integer entityType, UUID entityId, UUID targetId, String content) {
        return idempotencyGuard.executeRequired(
                "content:create_comment",
                userId,
                idempotencyKey,
                UUID.class,
                () -> commentContentPort.addComment(userId, postId, entityType, entityId, targetId, content)
        );
    }

    @Override
    public void updateComment(UUID userId, UUID postId, UUID commentId, String content) {
        commentContentPort.updateComment(userId, postId, commentId, content);
    }
}
