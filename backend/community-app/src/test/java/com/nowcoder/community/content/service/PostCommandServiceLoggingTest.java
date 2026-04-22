package com.nowcoder.community.content.service;

import com.nowcoder.community.content.app.post.AdminDeletePostUseCase;
import com.nowcoder.community.content.app.post.CreatePostUseCase;
import com.nowcoder.community.content.app.post.DeleteOwnPostUseCase;
import com.nowcoder.community.content.app.post.MarkPostWonderfulUseCase;
import com.nowcoder.community.content.app.post.TopPostUseCase;
import com.nowcoder.community.content.app.post.UpdatePostUseCase;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.score.PostScoreQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PostCommandServiceLoggingTest {

    private CreatePostUseCase createPostUseCase;
    private UpdatePostUseCase updatePostUseCase;
    private DeleteOwnPostUseCase deleteOwnPostUseCase;
    private TopPostUseCase topPostUseCase;
    private MarkPostWonderfulUseCase markPostWonderfulUseCase;
    private AdminDeletePostUseCase adminDeletePostUseCase;
    private PostCommandService service;

    @BeforeEach
    void setUp() {
        createPostUseCase = mock(CreatePostUseCase.class);
        updatePostUseCase = mock(UpdatePostUseCase.class);
        deleteOwnPostUseCase = mock(DeleteOwnPostUseCase.class);
        topPostUseCase = mock(TopPostUseCase.class);
        markPostWonderfulUseCase = mock(MarkPostWonderfulUseCase.class);
        adminDeletePostUseCase = mock(AdminDeletePostUseCase.class);
        service = new PostCommandService(
                createPostUseCase,
                updatePostUseCase,
                deleteOwnPostUseCase,
                topPostUseCase,
                markPostWonderfulUseCase,
                adminDeletePostUseCase
        );
    }

    @Test
    void createPostShouldLogBusinessEvent(CapturedOutput output) {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        UUID categoryId = uuid(3);
        when(createPostUseCase.createPost(userId, "Title", "Content", categoryId, List.of("java"))).thenReturn(postId);

        UUID createdPostId = service.createPost(userId, "Title", "Content", categoryId, List.of("java"));

        assertThat(createdPostId).isEqualTo(postId);
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
    void updatePostShouldLogBusinessEvent(CapturedOutput output) {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        UUID categoryId = uuid(3);
        service.updatePost(userId, postId, "Updated", "Body", categoryId, List.of("spring"));

        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_update")
                .contains("community.outcome=success")
                .contains("user.id=" + userId)
                .contains("community.post_category_id=" + categoryId)
                .contains("community.target_type=post")
                .contains("community.target_id=" + postId);
    }

    @Test
    void deletePostByAuthorShouldLogBusinessEvent(CapturedOutput output) {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        service.deletePostByAuthor(userId, postId);

        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_delete")
                .contains("community.outcome=success")
                .contains("community.reason_code=author_delete")
                .contains("user.id=" + userId)
                .contains("community.target_type=post")
                .contains("community.target_id=" + postId);
    }

    @Test
    void topPostShouldLogBusinessEvent(CapturedOutput output) {
        UUID userId = uuid(9);
        UUID postId = uuid(101);
        service.topPost(userId, postId);

        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_top")
                .contains("community.outcome=success")
                .contains("user.id=" + userId)
                .contains("community.target_type=post")
                .contains("community.target_id=" + postId);
    }

    @Test
    void markWonderfulShouldLogBusinessEvent(CapturedOutput output) {
        UUID userId = uuid(9);
        UUID postId = uuid(101);
        service.markWonderful(userId, postId);

        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_wonderful")
                .contains("community.outcome=success")
                .contains("user.id=" + userId)
                .contains("community.target_type=post")
                .contains("community.target_id=" + postId);
    }

    @Test
    void adminDeleteShouldLogBusinessEvent(CapturedOutput output) {
        UUID userId = uuid(99);
        UUID postId = uuid(101);
        service.adminDelete(userId, postId);

        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_delete")
                .contains("community.outcome=success")
                .contains("community.reason_code=admin_delete")
                .contains("user.id=" + userId)
                .contains("community.target_type=post")
                .contains("community.target_id=" + postId);
    }
}
