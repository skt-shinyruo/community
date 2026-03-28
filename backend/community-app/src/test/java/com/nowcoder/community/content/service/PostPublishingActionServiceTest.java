package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.model.PostCreateResult;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostPublishingActionServiceTest {

    @Test
    void createShouldEscapeFilterAndDelegateCommandThroughIdempotencyGuard() {
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostCommandService postCommandService = mock(PostCommandService.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);

        when(sensitiveFilter.filter("<title>")).thenReturn("title");
        when(sensitiveFilter.filter("<content>")).thenReturn("content");
        when(postCommandService.createPost(eq(7), eq("title"), eq("content"), eq(1), eq(List.of("java")))).thenReturn(99);
        when(idempotencyGuard.executeRequired(eq("content:create_post"), eq(7), eq("idem-1"), eq(PostCreateResult.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<PostCreateResult>>getArgument(4).get());

        PostPublishingActionService service = new PostPublishingActionService(
                sensitiveFilter,
                postCommandService,
                idempotencyGuard,
                new ContentTextCodec(new ContentRenderProperties())
        );

        PostCreateResult response = service.create(7, "idem-1", "<title>", "<content>", 1, List.of("java"));

        assertThat(response.postId()).isEqualTo(99);
        verify(postCommandService).createPost(7, "title", "content", 1, List.of("java"));
        verify(idempotencyGuard).executeRequired(eq("content:create_post"), eq(7), eq("idem-1"), eq(PostCreateResult.class), any());
    }

    @Test
    void updateAndDeleteByAuthorShouldDelegateCommandsWithoutFacadeLayer() {
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostCommandService postCommandService = mock(PostCommandService.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);

        when(sensitiveFilter.filter("<title>")).thenReturn("title");
        when(sensitiveFilter.filter("<content>")).thenReturn("content");

        PostPublishingActionService service = new PostPublishingActionService(
                sensitiveFilter,
                postCommandService,
                idempotencyGuard,
                new ContentTextCodec(new ContentRenderProperties())
        );

        service.updatePost(7, 101, "<title>", "<content>", 2, List.of("spring"));
        service.deleteByAuthor(7, 101);

        verify(postCommandService).updatePost(7, 101, "title", "content", 2, List.of("spring"));
        verify(postCommandService).deletePostByAuthor(7, 101);
    }
}
