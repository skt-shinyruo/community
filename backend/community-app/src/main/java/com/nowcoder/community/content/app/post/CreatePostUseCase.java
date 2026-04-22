package com.nowcoder.community.content.app.post;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.service.CategoryService;
import com.nowcoder.community.content.service.PostService;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.service.UserModerationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class CreatePostUseCase {

    private final PostService postService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final UserModerationGuard moderationGuard;
    private final PostDomainEventPublisher domainEventPublisher;
    private final PostWriteSideEffectScheduler postWriteSideEffectScheduler;

    public CreatePostUseCase(
            PostService postService,
            CategoryService categoryService,
            TagService tagService,
            UserModerationGuard moderationGuard,
            PostDomainEventPublisher domainEventPublisher,
            PostWriteSideEffectScheduler postWriteSideEffectScheduler
    ) {
        this.postService = postService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.moderationGuard = moderationGuard;
        this.domainEventPublisher = domainEventPublisher;
        this.postWriteSideEffectScheduler = postWriteSideEffectScheduler;
    }

    @Transactional
    public UUID createPost(UUID userId, String title, String content, UUID categoryId, List<String> tags) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        moderationGuard.assertCanSpeak(userId);
        categoryService.assertExists(categoryId);

        DiscussPost post = new DiscussPost();
        post.setUserId(userId);
        post.setCategoryId(categoryId);
        post.setTitle(title);
        post.setContent(content);
        post.setType(0);
        post.setStatus(0);
        post.setCreateTime(new Date());
        post.setCommentCount(0);
        post.setScore(0.0);

        UUID postId = postService.create(post);
        tagService.bindTagsToPost(postId, tags);
        domainEventPublisher.postPublished(postId);
        postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
        return postId;
    }
}
