package com.nowcoder.community.content.api.action;

import com.nowcoder.community.content.api.model.PostCreateResult;

import java.util.List;

public interface PostPublishingActionApi {

    PostCreateResult create(int userId, String idempotencyKey, String title, String content, Integer categoryId, List<String> tags);

    void updatePost(int userId, int postId, String title, String content, Integer categoryId, List<String> tags);

    void deleteByAuthor(int userId, int postId);
}
