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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(createPostUseCase.createPost(7, "Title", "Content", 3, List.of("java"))).thenReturn(101);

        int postId = service.createPost(7, "Title", "Content", 3, List.of("java"));

        assertThat(postId).isEqualTo(101);
        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_create")
                .contains("community.outcome=success")
                .contains("user.id=7")
                .contains("community.post_category_id=3")
                .contains("community.target_type=post")
                .contains("community.target_id=101");
    }

    @Test
    void updatePostShouldLogBusinessEvent(CapturedOutput output) {
        service.updatePost(7, 101, "Updated", "Body", 3, List.of("spring"));

        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_update")
                .contains("community.outcome=success")
                .contains("user.id=7")
                .contains("community.post_category_id=3")
                .contains("community.target_type=post")
                .contains("community.target_id=101");
    }

    @Test
    void deletePostByAuthorShouldLogBusinessEvent(CapturedOutput output) {
        service.deletePostByAuthor(7, 101);

        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_delete")
                .contains("community.outcome=success")
                .contains("community.reason_code=author_delete")
                .contains("user.id=7")
                .contains("community.target_type=post")
                .contains("community.target_id=101");
    }

    @Test
    void topPostShouldLogBusinessEvent(CapturedOutput output) {
        service.topPost(9, 101);

        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_top")
                .contains("community.outcome=success")
                .contains("user.id=9")
                .contains("community.target_type=post")
                .contains("community.target_id=101");
    }

    @Test
    void markWonderfulShouldLogBusinessEvent(CapturedOutput output) {
        service.markWonderful(9, 101);

        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_wonderful")
                .contains("community.outcome=success")
                .contains("user.id=9")
                .contains("community.target_type=post")
                .contains("community.target_id=101");
    }

    @Test
    void adminDeleteShouldLogBusinessEvent(CapturedOutput output) {
        service.adminDelete(99, 101);

        assertThat(output.getAll())
                .contains("community.category=business")
                .contains("community.action=post_delete")
                .contains("community.outcome=success")
                .contains("community.reason_code=admin_delete")
                .contains("user.id=99")
                .contains("community.target_type=post")
                .contains("community.target_id=101");
    }

}
