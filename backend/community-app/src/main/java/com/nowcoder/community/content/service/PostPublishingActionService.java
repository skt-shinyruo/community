package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.action.PostPublishingActionApi;
import com.nowcoder.community.content.api.model.PostCreateResult;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostPublishingActionService implements PostPublishingActionApi {

    private final SensitiveFilter sensitiveFilter;
    private final PostCommandService postCommandService;
    private final IdempotencyGuard idempotencyGuard;
    private final ContentTextCodec textCodec;

    public PostPublishingActionService(
            SensitiveFilter sensitiveFilter,
            PostCommandService postCommandService,
            IdempotencyGuard idempotencyGuard,
            ContentTextCodec textCodec
    ) {
        this.sensitiveFilter = sensitiveFilter;
        this.postCommandService = postCommandService;
        this.idempotencyGuard = idempotencyGuard;
        this.textCodec = textCodec;
    }

    @Override
    public PostCreateResult create(int userId, String idempotencyKey, String title, String content, Integer categoryId, List<String> tags) {
        return idempotencyGuard.executeRequired("content:create_post", userId, idempotencyKey, PostCreateResult.class, () -> {
            String safeTitle = sanitize(title);
            String safeContent = sanitize(content);
            int postId = postCommandService.createPost(userId, safeTitle, safeContent, categoryId, tags);
            return new PostCreateResult(postId);
        });
    }

    @Override
    public void updatePost(int userId, int postId, String title, String content, Integer categoryId, List<String> tags) {
        String safeTitle = sanitize(title);
        String safeContent = sanitize(content);
        postCommandService.updatePost(userId, postId, safeTitle, safeContent, categoryId, tags);
    }

    @Override
    public void deleteByAuthor(int userId, int postId) {
        postCommandService.deletePostByAuthor(userId, postId);
    }

    private String sanitize(String value) {
        String trimmed = value == null ? "" : value.trim();
        return sensitiveFilter.filter(textCodec.escapeOnWrite(trimmed));
    }
}
