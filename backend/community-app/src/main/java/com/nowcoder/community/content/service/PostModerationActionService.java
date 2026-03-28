package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.action.PostModerationActionApi;
import org.springframework.stereotype.Service;

@Service
public class PostModerationActionService implements PostModerationActionApi {

    private final PostCommandService postCommandService;

    public PostModerationActionService(PostCommandService postCommandService) {
        this.postCommandService = postCommandService;
    }

    @Override
    public void top(int actorUserId, int postId) {
        postCommandService.topPost(actorUserId, postId);
    }

    @Override
    public void wonderful(int actorUserId, int postId) {
        postCommandService.markWonderful(actorUserId, postId);
    }

    @Override
    public void delete(int actorUserId, int postId) {
        postCommandService.adminDelete(actorUserId, postId);
    }
}
