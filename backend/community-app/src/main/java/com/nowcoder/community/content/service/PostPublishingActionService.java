package com.nowcoder.community.content.service;

import com.nowcoder.community.content.app.post.CreatePostUseCase;
import com.nowcoder.community.content.app.post.DeleteOwnPostUseCase;
import com.nowcoder.community.content.app.post.UpdatePostUseCase;
import com.nowcoder.community.content.api.action.PostPublishingActionApi;
import com.nowcoder.community.content.api.model.PostCreateResult;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PostPublishingActionService implements PostPublishingActionApi {

    private final SensitiveFilter sensitiveFilter;
    private final CreatePostUseCase createPostUseCase;
    private final UpdatePostUseCase updatePostUseCase;
    private final DeleteOwnPostUseCase deleteOwnPostUseCase;
    private final IdempotencyGuard idempotencyGuard;
    private final ContentTextCodec textCodec;

    public PostPublishingActionService(
            SensitiveFilter sensitiveFilter,
            CreatePostUseCase createPostUseCase,
            UpdatePostUseCase updatePostUseCase,
            DeleteOwnPostUseCase deleteOwnPostUseCase,
            IdempotencyGuard idempotencyGuard,
            ContentTextCodec textCodec
    ) {
        this.sensitiveFilter = sensitiveFilter;
        this.createPostUseCase = createPostUseCase;
        this.updatePostUseCase = updatePostUseCase;
        this.deleteOwnPostUseCase = deleteOwnPostUseCase;
        this.idempotencyGuard = idempotencyGuard;
        this.textCodec = textCodec;
    }

    @Override
    public PostCreateResult create(UUID userId, String idempotencyKey, String title, String content, UUID categoryId, List<String> tags) {
        return idempotencyGuard.executeRequired("content:create_post", userId, idempotencyKey, PostCreateResult.class, () -> {
            String safeTitle = sanitize(title);
            String safeContent = sanitize(content);
            UUID postId = createPostUseCase.createPost(userId, safeTitle, safeContent, categoryId, tags);
            return new PostCreateResult(postId);
        });
    }

    @Override
    public void updatePost(UUID userId, UUID postId, String title, String content, UUID categoryId, List<String> tags) {
        String safeTitle = sanitize(title);
        String safeContent = sanitize(content);
        updatePostUseCase.updatePost(userId, postId, safeTitle, safeContent, categoryId, tags);
    }

    @Override
    public void deleteByAuthor(UUID userId, UUID postId) {
        deleteOwnPostUseCase.deletePostByAuthor(userId, postId);
    }

    private String sanitize(String value) {
        String trimmed = value == null ? "" : value.trim();
        return sensitiveFilter.filter(textCodec.escapeOnWrite(trimmed));
    }
}
