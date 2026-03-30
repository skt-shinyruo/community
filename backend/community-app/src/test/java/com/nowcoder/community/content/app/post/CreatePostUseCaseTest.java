package com.nowcoder.community.content.app.post;

import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.service.CategoryService;
import com.nowcoder.community.content.service.PostService;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.service.UserModerationGuard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreatePostUseCaseTest {

    @Test
    void createPostShouldOwnWritePathOrchestrationWithoutGiantCommandService() {
        PostService postService = mock(PostService.class);
        CategoryService categoryService = mock(CategoryService.class);
        TagService tagService = mock(TagService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        PostDomainEventPublisher domainEventPublisher = mock(PostDomainEventPublisher.class);
        PostWriteSideEffectScheduler postWriteSideEffectScheduler = mock(PostWriteSideEffectScheduler.class);
        when(postService.create(any(DiscussPost.class))).thenReturn(101);

        CreatePostUseCase useCase = new CreatePostUseCase(
                postService,
                categoryService,
                tagService,
                moderationGuard,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );

        int postId = useCase.createPost(7, "Title", "Content", 3, List.of("java"));

        assertThat(postId).isEqualTo(101);
        verify(moderationGuard).assertCanSpeak(7);
        verify(categoryService).assertExists(3);
        verify(postService).create(any(DiscussPost.class));
        verify(tagService).bindTagsToPost(101, List.of("java"));
        verify(domainEventPublisher).postPublished(101);
        verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(101);
    }
}
