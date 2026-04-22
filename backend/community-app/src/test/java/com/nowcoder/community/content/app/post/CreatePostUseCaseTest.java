package com.nowcoder.community.content.app.post;

import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.service.CategoryService;
import com.nowcoder.community.content.service.PostService;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.service.UserModerationGuard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
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
        UUID userId = uuid(7);
        UUID categoryId = uuid(3);
        UUID postId = uuid(101);
        when(postService.create(any(DiscussPost.class))).thenReturn(postId);

        CreatePostUseCase useCase = new CreatePostUseCase(
                postService,
                categoryService,
                tagService,
                moderationGuard,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );

        UUID createdPostId = useCase.createPost(userId, "Title", "Content", categoryId, List.of("java"));

        assertThat(createdPostId).isEqualTo(postId);
        verify(moderationGuard).assertCanSpeak(userId);
        verify(categoryService).assertExists(categoryId);
        verify(postService).create(any(DiscussPost.class));
        verify(tagService).bindTagsToPost(postId, List.of("java"));
        verify(domainEventPublisher).postPublished(postId);
        verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(postId);
    }
}
