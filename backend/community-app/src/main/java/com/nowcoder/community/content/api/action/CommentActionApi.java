package com.nowcoder.community.content.api.action;

import java.util.UUID;

public interface CommentActionApi {

    UUID addComment(UUID userId, String idempotencyKey, UUID postId, UUID parentCommentId, String content);

    void updateComment(UUID userId, UUID postId, UUID commentId, String content);
}
