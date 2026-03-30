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

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;

@Service
public class UpdatePostUseCase {

    private final PostService postService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final UserModerationGuard moderationGuard;
    private final PostDomainEventPublisher domainEventPublisher;
    private final PostWriteSideEffectScheduler postWriteSideEffectScheduler;

    public UpdatePostUseCase(
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
    public void updatePost(int actorUserId, int postId, String title, String content, Integer categoryId, List<String> tags) {
        if (actorUserId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        moderationGuard.assertCanSpeak(actorUserId);
        categoryService.assertExists(categoryId);

        DiscussPost existed = postService.getByIdAllowDeleted(postId);
        if (existed.getStatus() == 2) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        if (existed.getUserId() != actorUserId) {
            throw new BusinessException(FORBIDDEN, "只能编辑自己的帖子");
        }
        if (existed.getCreateTime() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "帖子时间非法");
        }

        Date now = new Date();
        long windowMillis = 24L * 3600 * 1000;
        if (now.getTime() - existed.getCreateTime().getTime() > windowMillis) {
            throw new BusinessException(FORBIDDEN, "已超过可编辑时间（24h）");
        }

        postService.updatePostContent(postId, title, content, categoryId, now);
        tagService.replaceTagsForPost(postId, tags);
        domainEventPublisher.postUpdated(postId);
        postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
    }
}
