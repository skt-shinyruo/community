package com.nowcoder.community.content.service;

import com.nowcoder.community.content.app.post.AdminDeletePostUseCase;
import com.nowcoder.community.content.app.post.MarkPostWonderfulUseCase;
import com.nowcoder.community.content.app.post.TopPostUseCase;
import com.nowcoder.community.content.api.action.PostModerationActionApi;
import org.springframework.stereotype.Service;

@Service
public class PostModerationActionService implements PostModerationActionApi {

    private final TopPostUseCase topPostUseCase;
    private final MarkPostWonderfulUseCase markPostWonderfulUseCase;
    private final AdminDeletePostUseCase adminDeletePostUseCase;

    public PostModerationActionService(
            TopPostUseCase topPostUseCase,
            MarkPostWonderfulUseCase markPostWonderfulUseCase,
            AdminDeletePostUseCase adminDeletePostUseCase
    ) {
        this.topPostUseCase = topPostUseCase;
        this.markPostWonderfulUseCase = markPostWonderfulUseCase;
        this.adminDeletePostUseCase = adminDeletePostUseCase;
    }

    @Override
    public void top(int actorUserId, int postId) {
        topPostUseCase.topPost(actorUserId, postId);
    }

    @Override
    public void wonderful(int actorUserId, int postId) {
        markPostWonderfulUseCase.markWonderful(actorUserId, postId);
    }

    @Override
    public void delete(int actorUserId, int postId) {
        adminDeletePostUseCase.adminDelete(actorUserId, postId);
    }
}
