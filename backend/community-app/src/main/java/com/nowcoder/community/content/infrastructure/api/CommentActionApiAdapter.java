package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.action.CommentActionApi;
import com.nowcoder.community.content.application.CommentApplicationService;
import com.nowcoder.community.content.application.command.CreateCommentCommand;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CommentActionApiAdapter implements CommentActionApi {

    private final CommentApplicationService commentApplicationService;

    public CommentActionApiAdapter(CommentApplicationService commentApplicationService) {
        this.commentApplicationService = commentApplicationService;
    }

    @Override
    public UUID addComment(
            UUID userId,
            String idempotencyKey,
            UUID postId,
            UUID parentCommentId,
            String content
    ) {
        return commentApplicationService.create(
                idempotencyKey,
                new CreateCommentCommand(userId, postId, parentCommentId, content)
        ).commentId();
    }

    @Override
    public void updateComment(UUID userId, UUID postId, UUID commentId, String content) {
        commentApplicationService.updateComment(userId, postId, commentId, content);
    }
}
