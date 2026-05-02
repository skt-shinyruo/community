package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.service.PostModerationDomainService;
import com.nowcoder.community.social.api.action.SocialLikeCleanupActionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class PostModerationApplicationService {

    private final PostModerationDomainService domainService;
    private final PostRepository postRepository;
    private final PostDomainEventPublisher domainEventPublisher;
    private final PostWriteSideEffectScheduler postWriteSideEffectScheduler;
    private final SocialLikeCleanupActionApi socialLikeCleanupActionApi;
    private final PostBusinessEventLogger postBusinessEventLogger;

    public PostModerationApplicationService(
            PostModerationDomainService domainService,
            PostRepository postRepository,
            PostDomainEventPublisher domainEventPublisher,
            PostWriteSideEffectScheduler postWriteSideEffectScheduler,
            SocialLikeCleanupActionApi socialLikeCleanupActionApi,
            PostBusinessEventLogger postBusinessEventLogger
    ) {
        this.domainService = domainService;
        this.postRepository = postRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.postWriteSideEffectScheduler = postWriteSideEffectScheduler;
        this.socialLikeCleanupActionApi = socialLikeCleanupActionApi;
        this.postBusinessEventLogger = postBusinessEventLogger;
    }

    @Transactional
    public void top(UUID actorUserId, UUID postId) {
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        domainService.assertCanModeratePost(actorUserId, post);
        postRepository.markTop(postId);
        domainEventPublisher.postUpdated(postId);
        postBusinessEventLogger.postTop(actorUserId, postId);
    }

    @Transactional
    public void wonderful(UUID actorUserId, UUID postId) {
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        domainService.assertCanModeratePost(actorUserId, post);
        postRepository.markWonderful(postId);
        domainEventPublisher.postUpdated(postId);
        postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
        postBusinessEventLogger.postWonderful(actorUserId, postId);
    }

    @Transactional
    public void delete(UUID actorUserId, UUID postId) {
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        if (!domainService.shouldAdminDelete(actorUserId, post)) {
            return;
        }
        boolean changed = postRepository.markDeletedByAdmin(postId, actorUserId, new Date());
        if (!changed) {
            return;
        }
        applyPostDeleteSideEffects(postId);
        postBusinessEventLogger.postDeleteByAdmin(actorUserId, postId);
    }

    @Transactional
    public void deleteByModeration(UUID actorUserId, UUID postId) {
        boolean changed = postRepository.markDeletedByAdmin(postId, actorUserId, new Date());
        if (!changed) {
            return;
        }
        applyPostDeleteSideEffects(postId);
        postBusinessEventLogger.postDeleteByAdmin(actorUserId, postId);
    }

    private void applyPostDeleteSideEffects(UUID postId) {
        domainEventPublisher.postDeleted(postId);
        AfterCommitExecutor.runAfterCommit(() -> socialLikeCleanupActionApi.cleanupEntityLikes(EntityTypes.POST, postId));
        postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
    }
}
