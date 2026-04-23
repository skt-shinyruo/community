package com.nowcoder.community.content.service;

import com.nowcoder.community.content.app.post.CreatePostUseCase;
import com.nowcoder.community.content.app.post.DeleteOwnPostUseCase;
import com.nowcoder.community.content.app.post.UpdatePostUseCase;
import com.nowcoder.community.content.api.model.PostCreateResult;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostPublishingApplicationServiceTest {

    @Test
    void createShouldEscapeFilterAndDelegateCommandThroughIdempotencyGuard() {
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        CreatePostUseCase createPostUseCase = mock(CreatePostUseCase.class);
        UpdatePostUseCase updatePostUseCase = mock(UpdatePostUseCase.class);
        DeleteOwnPostUseCase deleteOwnPostUseCase = mock(DeleteOwnPostUseCase.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        UUID userId = uuid(7);
        UUID categoryId = uuid(1);
        UUID postId = uuid(99);

        when(sensitiveFilter.filter("<title>")).thenReturn("title");
        when(sensitiveFilter.filter("<content>")).thenReturn("content");
        when(createPostUseCase.createPost(eq(userId), eq("title"), eq("content"), eq(categoryId), eq(List.of("java")))).thenReturn(postId);
        when(idempotencyGuard.executeRequired(eq("content:create_post"), eq(userId), anyString(), eq(PostCreateResult.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<PostCreateResult>>getArgument(4).get());

        PostPublishingApplicationService service = new PostPublishingApplicationService(
                sensitiveFilter,
                createPostUseCase,
                updatePostUseCase,
                deleteOwnPostUseCase,
                idempotencyGuard,
                new ContentTextCodec(new ContentRenderProperties())
        );

        UUID createdPostId = service.createPost(userId, "idem-1", "<title>", "<content>", categoryId, List.of("java"));
        PostCreateResult response = service.create(userId, "idem-2", "<title>", "<content>", categoryId, List.of("java"));

        assertThat(response.postId()).isEqualTo(postId);
        assertThat(createdPostId).isEqualTo(postId);
        verify(createPostUseCase, times(2)).createPost(userId, "title", "content", categoryId, List.of("java"));
        verify(idempotencyGuard).executeRequired(eq("content:create_post"), eq(userId), eq("idem-1"), eq(PostCreateResult.class), any());
        verify(idempotencyGuard).executeRequired(eq("content:create_post"), eq(userId), eq("idem-2"), eq(PostCreateResult.class), any());
    }

    @Test
    void updateAndDeleteByAuthorShouldDelegateCommandsWithoutFacadeLayer() {
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        CreatePostUseCase createPostUseCase = mock(CreatePostUseCase.class);
        UpdatePostUseCase updatePostUseCase = mock(UpdatePostUseCase.class);
        DeleteOwnPostUseCase deleteOwnPostUseCase = mock(DeleteOwnPostUseCase.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        UUID categoryId = uuid(2);

        when(sensitiveFilter.filter("<title>")).thenReturn("title");
        when(sensitiveFilter.filter("<content>")).thenReturn("content");

        PostPublishingApplicationService service = new PostPublishingApplicationService(
                sensitiveFilter,
                createPostUseCase,
                updatePostUseCase,
                deleteOwnPostUseCase,
                idempotencyGuard,
                new ContentTextCodec(new ContentRenderProperties())
        );

        service.updatePost(userId, postId, "<title>", "<content>", categoryId, List.of("spring"));
        service.deleteByAuthor(userId, postId);

        verify(updatePostUseCase).updatePost(userId, postId, "title", "content", categoryId, List.of("spring"));
        verify(deleteOwnPostUseCase).deletePostByAuthor(userId, postId);
    }
}
