package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.action.PostModerationActionApi;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostModerationActionApiAdapter implements PostModerationActionApi {

    private final com.nowcoder.community.content.application.PostModerationApplicationService delegate;

    public PostModerationActionApiAdapter(com.nowcoder.community.content.application.PostModerationApplicationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void top(UUID actorUserId, UUID postId) {
        delegate.top(actorUserId, postId);
    }

    @Override
    public void wonderful(UUID actorUserId, UUID postId) {
        delegate.wonderful(actorUserId, postId);
    }

    @Override
    public void delete(UUID actorUserId, UUID postId) {
        delegate.delete(actorUserId, postId);
    }
}
