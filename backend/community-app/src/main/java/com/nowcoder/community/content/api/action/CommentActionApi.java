package com.nowcoder.community.content.api.action;

public interface CommentActionApi {

    int addComment(int userId, String idempotencyKey, int postId, Integer entityType, Integer entityId, Integer targetId, String content);

    void updateComment(int userId, int postId, int commentId, String content);
}
