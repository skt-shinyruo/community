package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.content.application.command.CreatePostCommand;
import com.nowcoder.community.content.application.ContentSanitizer;
import com.nowcoder.community.content.application.result.PostCreateResult;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.CategoryRepository;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.repository.PostTagRepository;
import com.nowcoder.community.content.domain.service.PostPublishingDomainService;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.social.api.action.SocialLikeCleanupActionApi;
import com.nowcoder.community.user.api.action.UserPointsAwardActionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class PostPublishingApplicationService {

    private static final String CREATE_POST_IDEMPOTENCY_SCOPE = "content:create_post";

    private final ContentSanitizer sensitiveFilter;
    private final IdempotencyGuard idempotencyGuard;
    private final ContentTextCodec textCodec;
    private final PostBusinessEventLogger postBusinessEventLogger;
    private final UserModerationGuard moderationGuard;
    private final PostPublishingDomainService domainService;
    private final PostRepository postRepository;
    private final CategoryRepository categoryRepository;
    private final PostTagRepository postTagRepository;
    private final PostDomainEventPublisher domainEventPublisher;
    private final PostWriteSideEffectScheduler postWriteSideEffectScheduler;
    private final SocialLikeCleanupActionApi socialLikeCleanupActionApi;
    private final UserPointsAwardActionApi pointsAwardService;
    private final GrowthTaskProgressActionApi taskProgressTriggerService;

    public PostPublishingApplicationService(
            ContentSanitizer sensitiveFilter,
            IdempotencyGuard idempotencyGuard,
            ContentTextCodec textCodec,
            PostBusinessEventLogger postBusinessEventLogger,
            UserModerationGuard moderationGuard,
            PostPublishingDomainService domainService,
            PostRepository postRepository,
            CategoryRepository categoryRepository,
            PostTagRepository postTagRepository,
            PostDomainEventPublisher domainEventPublisher,
            PostWriteSideEffectScheduler postWriteSideEffectScheduler,
            SocialLikeCleanupActionApi socialLikeCleanupActionApi,
            UserPointsAwardActionApi pointsAwardService,
            GrowthTaskProgressActionApi taskProgressTriggerService
    ) {
        this.sensitiveFilter = sensitiveFilter;
        this.idempotencyGuard = idempotencyGuard;
        this.textCodec = textCodec;
        this.postBusinessEventLogger = postBusinessEventLogger;
        this.moderationGuard = moderationGuard;
        this.domainService = domainService;
        this.postRepository = postRepository;
        this.categoryRepository = categoryRepository;
        this.postTagRepository = postTagRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.postWriteSideEffectScheduler = postWriteSideEffectScheduler;
        this.socialLikeCleanupActionApi = socialLikeCleanupActionApi;
        this.pointsAwardService = pointsAwardService;
        this.taskProgressTriggerService = taskProgressTriggerService;
    }

    @Transactional
    public PostCreateResult create(UUID userId, String idempotencyKey, String title, String content, UUID categoryId, List<String> tags) {
        return create(idempotencyKey, new CreatePostCommand(userId, title, content, categoryId, tags));
    }

    @Transactional
    public PostCreateResult create(String idempotencyKey, CreatePostCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        UUID userId = command.userId();
        return idempotencyGuard.executeRequired(CREATE_POST_IDEMPOTENCY_SCOPE, userId, idempotencyKey, PostCreateResult.class, () -> {
            moderationGuard.assertCanSpeak(userId);
            categoryRepository.assertExists(command.categoryId());
            PostDraft draft = domainService.createDraft(userId, sanitize(command.title()), sanitize(command.content()), command.categoryId());
            UUID postId = postRepository.create(draft);
            postTagRepository.bindTagsToPost(postId, command.tags());
            pointsAwardService.awardPostPublished(postId, userId);
            taskProgressTriggerService.triggerPostPublished(postId, userId, draft.createTime().toInstant());
            domainEventPublisher.postPublished(postId);
            postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
            postBusinessEventLogger.postCreate(userId, command.categoryId(), postId);
            return new PostCreateResult(postId);
        });
    }

    @Transactional
    public void updatePost(UUID userId, UUID postId, String title, String content, UUID categoryId, List<String> tags) {
        moderationGuard.assertCanSpeak(userId);
        categoryRepository.assertExists(categoryId);
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        Date now = new Date();
        domainService.assertEditableByAuthor(post, userId, now);
        postRepository.updateContent(postId, sanitize(title), sanitize(content), categoryId, now);
        postTagRepository.replaceTagsForPost(postId, tags);
        domainEventPublisher.postUpdated(postId);
        postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
        postBusinessEventLogger.postUpdate(userId, categoryId, postId);
    }

    @Transactional
    public void deleteByAuthor(UUID userId, UUID postId) {
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        domainService.assertDeletableByAuthor(post, userId);
        boolean changed = postRepository.markDeletedByAuthor(postId, userId, new Date());
        if (!changed) {
            return;
        }
        domainEventPublisher.postDeleted(postId);
        AfterCommitExecutor.runAfterCommit(() -> socialLikeCleanupActionApi.cleanupEntityLikes(EntityTypes.POST, postId));
        postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
        postBusinessEventLogger.postDeleteByAuthor(userId, postId);
    }

    private String sanitize(String value) {
        String trimmed = value == null ? "" : value.trim();
        return sensitiveFilter.filter(textCodec.escapeOnWrite(trimmed));
    }
}
