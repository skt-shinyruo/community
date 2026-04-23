package com.nowcoder.community.content.service;

import com.nowcoder.community.content.app.post.CreatePostUseCase;
import com.nowcoder.community.content.app.post.DeleteOwnPostUseCase;
import com.nowcoder.community.content.app.post.UpdatePostUseCase;
import com.nowcoder.community.content.api.model.PostCreateResult;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

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

@ExtendWith(OutputCaptureExtension.class)
class PostPublishingApplicationServiceTest {

    private SensitiveFilter sensitiveFilter;
    private CreatePostUseCase createPostUseCase;
    private UpdatePostUseCase updatePostUseCase;
    private DeleteOwnPostUseCase deleteOwnPostUseCase;
    private IdempotencyGuard idempotencyGuard;
    private PostBusinessEventLogger postBusinessEventLogger;
    private PostPublishingApplicationService service;

    @BeforeEach
    void setUp() {
        sensitiveFilter = mock(SensitiveFilter.class);
        createPostUseCase = mock(CreatePostUseCase.class);
        updatePostUseCase = mock(UpdatePostUseCase.class);
        deleteOwnPostUseCase = mock(DeleteOwnPostUseCase.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        postBusinessEventLogger = new PostBusinessEventLogger();
        service = new PostPublishingApplicationService(
                sensitiveFilter,
                createPostUseCase,
                updatePostUseCase,
                deleteOwnPostUseCase,
                idempotencyGuard,
                new ContentTextCodec(new ContentRenderProperties()),
                postBusinessEventLogger
        );
    }

    @Test
    void createShouldEscapeFilterDelegateCommandThroughIdempotencyGuardAndLogBusinessEvent(CapturedOutput output) {
        UUID userId = uuid(7);
        UUID categoryId = uuid(1);
        UUID postId = uuid(99);

        when(sensitiveFilter.filter("<title>")).thenReturn("title");
        when(sensitiveFilter.filter("<content>")).thenReturn("content");
        when(createPostUseCase.createPost(eq(userId), eq("title"), eq("content"), eq(categoryId), eq(List.of("java")))).thenReturn(postId);
        when(idempotencyGuard.executeRequired(eq("content:create_post"), eq(userId), anyString(), eq(PostCreateResult.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<PostCreateResult>>getArgument(4).get());

        UUID createdPostId = service.createPost(userId, "idem-1", "<title>", "<content>", categoryId, List.of("java"));
        PostCreateResult response = service.create(userId, "idem-2", "<title>", "<content>", categoryId, List.of("java"));

        assertThat(response.postId()).isEqualTo(postId);
        assertThat(createdPostId).isEqualTo(postId);
        verify(createPostUseCase, times(2)).createPost(userId, "title", "content", categoryId, List.of("java"));
        verify(idempotencyGuard).executeRequired(eq("content:create_post"), eq(userId), eq("idem-1"), eq(PostCreateResult.class), any());
        verify(idempotencyGuard).executeRequired(eq("content:create_post"), eq(userId), eq("idem-2"), eq(PostCreateResult.class), any());
        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_create")
                .contains("community.outcome=success")
                .contains("user.id=" + userId)
                .contains("community.post_category_id=" + categoryId)
                .contains("community.target_type=post")
                .contains("community.target_id=" + postId);
    }

    @Test
    void updateAndDeleteByAuthorShouldDelegateCommandsWithoutFacadeLayerAndLogBusinessEvents(CapturedOutput output) {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        UUID categoryId = uuid(2);

        when(sensitiveFilter.filter("<title>")).thenReturn("title");
        when(sensitiveFilter.filter("<content>")).thenReturn("content");

        service.updatePost(userId, postId, "<title>", "<content>", categoryId, List.of("spring"));
        service.deleteByAuthor(userId, postId);

        verify(updatePostUseCase).updatePost(userId, postId, "title", "content", categoryId, List.of("spring"));
        verify(deleteOwnPostUseCase).deletePostByAuthor(userId, postId);
        assertThat(output.getAll())
                .contains("community.action=post_update")
                .contains("community.post_category_id=" + categoryId)
                .contains("community.target_id=" + postId)
                .contains("community.action=post_delete")
                .contains("community.reason_code=author_delete")
                .contains("user.id=" + userId);
    }
}
