package com.nowcoder.community.content.service;

import com.nowcoder.community.content.app.post.AdminDeletePostUseCase;
import com.nowcoder.community.content.app.post.MarkPostWonderfulUseCase;
import com.nowcoder.community.content.app.post.TopPostUseCase;
import com.nowcoder.community.content.api.action.PostModerationActionApi;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostModerationApplicationService implements PostModerationActionApi {

    private final TopPostUseCase topPostUseCase;
    private final MarkPostWonderfulUseCase markPostWonderfulUseCase;
    private final AdminDeletePostUseCase adminDeletePostUseCase;

    public PostModerationApplicationService(
            TopPostUseCase topPostUseCase,
            MarkPostWonderfulUseCase markPostWonderfulUseCase,
            AdminDeletePostUseCase adminDeletePostUseCase
    ) {
        this.topPostUseCase = topPostUseCase;
        this.markPostWonderfulUseCase = markPostWonderfulUseCase;
        this.adminDeletePostUseCase = adminDeletePostUseCase;
    }

    @Override
    public void top(UUID actorUserId, UUID postId) {
        topPostUseCase.topPost(actorUserId, postId);
    }

    @Override
    public void wonderful(UUID actorUserId, UUID postId) {
        markPostWonderfulUseCase.markWonderful(actorUserId, postId);
    }

    @Override
    public void delete(UUID actorUserId, UUID postId) {
        adminDeletePostUseCase.adminDelete(actorUserId, postId);
    }
}
