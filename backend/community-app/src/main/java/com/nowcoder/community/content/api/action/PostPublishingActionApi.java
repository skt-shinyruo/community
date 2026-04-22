package com.nowcoder.community.content.api.action;

import com.nowcoder.community.content.api.model.PostCreateResult;

import java.util.List;
import java.util.UUID;

public interface PostPublishingActionApi {

    PostCreateResult create(UUID userId, String idempotencyKey, String title, String content, UUID categoryId, List<String> tags);

    void updatePost(UUID userId, UUID postId, String title, String content, UUID categoryId, List<String> tags);

    void deleteByAuthor(UUID userId, UUID postId);
}
