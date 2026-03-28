package com.nowcoder.community.content.api.action;

public interface PostModerationActionApi {

    void top(int actorUserId, int postId);

    void wonderful(int actorUserId, int postId);

    void delete(int actorUserId, int postId);
}
