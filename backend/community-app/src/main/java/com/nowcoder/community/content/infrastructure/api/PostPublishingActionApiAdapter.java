package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.PostContentBlockPayload;
import com.nowcoder.community.content.application.command.CreatePostCommand;
import com.nowcoder.community.content.application.command.PostContentBlockCommand;
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
    public PostCreateResult create(UUID userId, String idempotencyKey, String title, UUID categoryId, List<String> tags, List<PostContentBlockPayload> blocks) {
        com.nowcoder.community.content.application.result.PostCreateResult result =
                postPublishingApplicationService.create(
                        idempotencyKey,
                        new CreatePostCommand(userId, title, categoryId, tags, toBlockCommands(blocks))
                );
        return new PostCreateResult(result.postId());
    }

    @Override
    public void updatePost(UUID userId, UUID postId, String title, UUID categoryId, List<String> tags, List<PostContentBlockPayload> blocks) {
        postPublishingApplicationService.updatePost(userId, postId, title, categoryId, tags, toBlockCommands(blocks));
    }

    @Override
    public void deleteByAuthor(UUID userId, UUID postId) {
        postPublishingApplicationService.deleteByAuthor(userId, postId);
    }

    private static List<PostContentBlockCommand> toBlockCommands(List<PostContentBlockPayload> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream()
                .map(block -> new PostContentBlockCommand(
                        block.type(),
                        block.text(),
                        block.assetId(),
                        block.language(),
                        block.caption(),
                        block.displayName(),
                        block.metadata()
                ))
                .toList();
    }
}
