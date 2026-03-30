package com.nowcoder.community.content.app.post;

import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.service.CategoryService;
import com.nowcoder.community.content.service.PostService;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.service.UserModerationGuard;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdatePostUseCaseTest {

    @Test
    void updatePostShouldOwnEditWritePathOrchestrationWithoutGiantCommandService() {
        PostService postService = mock(PostService.class);
        CategoryService categoryService = mock(CategoryService.class);
        TagService tagService = mock(TagService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        PostDomainEventPublisher domainEventPublisher = mock(PostDomainEventPublisher.class);
        PostWriteSideEffectScheduler postWriteSideEffectScheduler = mock(PostWriteSideEffectScheduler.class);
        when(postService.getByIdAllowDeleted(101)).thenReturn(recentOwnedPost(101, 7));

        UpdatePostUseCase useCase = new UpdatePostUseCase(
                postService,
                categoryService,
                tagService,
                moderationGuard,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );

        useCase.updatePost(7, 101, "Updated", "Body", 3, List.of("spring"));

        verify(moderationGuard).assertCanSpeak(7);
        verify(categoryService).assertExists(3);
        verify(postService).getByIdAllowDeleted(101);
        verify(postService).updatePostContent(eq(101), eq("Updated"), eq("Body"), eq(3), any(Date.class));
        verify(tagService).replaceTagsForPost(101, List.of("spring"));
        verify(domainEventPublisher).postUpdated(101);
        verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(101);
    }

    @Test
    void additionalPostCommandUseCasesShouldExist() {
        assertThat(DeleteOwnPostUseCase.class).isNotNull();
        assertThat(AdminDeletePostUseCase.class).isNotNull();
        assertThat(TopPostUseCase.class).isNotNull();
        assertThat(MarkPostWonderfulUseCase.class).isNotNull();
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
