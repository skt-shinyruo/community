package com.nowcoder.community.content.service;

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

    private PostService postService;
    private PostScoreQueue postScoreQueue;
    private CategoryService categoryService;
    private TagService tagService;
    private UserModerationGuard moderationGuard;
    private PostDomainEventPublisher domainEventPublisher;
    private PostCommandService service;

    @BeforeEach
    void setUp() {
        postService = mock(PostService.class);
        postScoreQueue = mock(PostScoreQueue.class);
        categoryService = mock(CategoryService.class);
        tagService = mock(TagService.class);
        moderationGuard = mock(UserModerationGuard.class);
        domainEventPublisher = mock(PostDomainEventPublisher.class);
        service = new PostCommandService(
                postService,
                postScoreQueue,
                categoryService,
                tagService,
                moderationGuard,
                domainEventPublisher
        );
    }

    @Test
    void createPostShouldLogBusinessEvent(CapturedOutput output) {
        when(postService.create(any(DiscussPost.class))).thenReturn(101);

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
        when(postService.getByIdAllowDeleted(101)).thenReturn(recentOwnedPost(101, 7));

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
        when(postService.getByIdAllowDeleted(101)).thenReturn(recentOwnedPost(101, 7));

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
        when(postService.getByIdAllowDeleted(101)).thenReturn(recentOwnedPost(101, 7));

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

    private DiscussPost recentOwnedPost(int postId, int ownerUserId) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(ownerUserId);
        post.setStatus(0);
        post.setCreateTime(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));
        return post;
    }
}
