package com.nowcoder.community.content.api.action;

import com.nowcoder.community.content.api.model.PostCreateResult;
import com.nowcoder.community.content.api.model.PostContentBlockPayload;

import java.util.List;
import java.util.UUID;

public interface PostPublishingActionApi {

    PostCreateResult create(UUID userId, String idempotencyKey, String title, UUID categoryId, List<String> tags, List<PostContentBlockPayload> blocks);

    void updatePost(UUID userId, UUID postId, String title, UUID categoryId, List<String> tags, List<PostContentBlockPayload> blocks);

    void deleteByAuthor(UUID userId, UUID postId);
}
