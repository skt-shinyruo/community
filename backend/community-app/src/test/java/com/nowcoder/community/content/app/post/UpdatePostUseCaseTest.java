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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.nowcoder.community.support.TestUuids.uuid;
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
        UUID postId = uuid(101);
        UUID actorUserId = uuid(7);
        UUID categoryId = uuid(3);
        when(postService.getByIdAllowDeleted(postId)).thenReturn(recentOwnedPost(postId, actorUserId));

        UpdatePostUseCase useCase = new UpdatePostUseCase(
                postService,
                categoryService,
                tagService,
                moderationGuard,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );

        useCase.updatePost(actorUserId, postId, "Updated", "Body", categoryId, List.of("spring"));

        verify(moderationGuard).assertCanSpeak(actorUserId);
        verify(categoryService).assertExists(categoryId);
        verify(postService).getByIdAllowDeleted(postId);
        verify(postService).updatePostContent(eq(postId), eq("Updated"), eq("Body"), eq(categoryId), any(Date.class));
        verify(tagService).replaceTagsForPost(postId, List.of("spring"));
        verify(domainEventPublisher).postUpdated(postId);
        verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(postId);
    }

    @Test
    void additionalPostCommandUseCasesShouldExist() {
        assertThat(DeleteOwnPostUseCase.class).isNotNull();
        assertThat(AdminDeletePostUseCase.class).isNotNull();
        assertThat(TopPostUseCase.class).isNotNull();
        assertThat(MarkPostWonderfulUseCase.class).isNotNull();
    }

    private DiscussPost recentOwnedPost(UUID postId, UUID ownerUserId) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(ownerUserId);
        post.setStatus(0);
        post.setCreateTime(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));
        return post;
    }
}
