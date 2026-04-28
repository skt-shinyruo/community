package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.application.PostPublishingApplicationService;
import com.nowcoder.community.content.api.action.PostPublishingActionApi;
import com.nowcoder.community.content.api.model.PostCreateResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PostPublishingActionApiAdapter implements PostPublishingActionApi {

    private final PostPublishingApplicationService postPublishingApplicationService;

    public PostPublishingActionApiAdapter(PostPublishingApplicationService postPublishingApplicationService) {
        this.postPublishingApplicationService = postPublishingApplicationService;
    }

    @Override
    public PostCreateResult create(UUID userId, String idempotencyKey, String title, String content, UUID categoryId, List<String> tags) {
        com.nowcoder.community.content.application.result.PostCreateResult result =
                postPublishingApplicationService.create(userId, idempotencyKey, title, content, categoryId, tags);
        return new PostCreateResult(result.postId());
    }

    @Override
    public void updatePost(UUID userId, UUID postId, String title, String content, UUID categoryId, List<String> tags) {
        postPublishingApplicationService.updatePost(userId, postId, title, content, categoryId, tags);
    }

    @Override
    public void deleteByAuthor(UUID userId, UUID postId) {
        postPublishingApplicationService.deleteByAuthor(userId, postId);
    }
}
