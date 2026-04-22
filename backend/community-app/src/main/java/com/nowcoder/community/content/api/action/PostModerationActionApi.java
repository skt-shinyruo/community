package com.nowcoder.community.content.api.action;

import java.util.UUID;

public interface PostModerationActionApi {

    void top(UUID actorUserId, UUID postId);

    void wonderful(UUID actorUserId, UUID postId);

    void delete(UUID actorUserId, UUID postId);
}
