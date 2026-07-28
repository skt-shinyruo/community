package com.nowcoder.community.content.application;

import com.nowcoder.community.content.api.action.PostModerationActionApi;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.service.PostModerationDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class PostModerationApplicationService implements PostModerationActionApi {

    private final PostModerationDomainService domainService;
    private final PostRepository postRepository;
    private final PostIntegrationEventPublisher integrationEventPublisher;
    private final PostMediaReferenceScheduler mediaReferenceScheduler;
    private final PostBusinessEventLogger postBusinessEventLogger;

    public PostModerationApplicationService(
            PostModerationDomainService domainService,
            PostRepository postRepository,
            PostIntegrationEventPublisher integrationEventPublisher,
            PostMediaReferenceScheduler mediaReferenceScheduler,
            PostBusinessEventLogger postBusinessEventLogger
    ) {
        this.domainService = domainService;
        this.postRepository = postRepository;
        this.integrationEventPublisher = integrationEventPublisher;
        this.mediaReferenceScheduler = mediaReferenceScheduler;
        this.postBusinessEventLogger = postBusinessEventLogger;
    }

    @Override
    @Transactional
    public void top(UUID actorUserId, UUID postId) {
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        domainService.assertCanModeratePost(actorUserId, post);
        postRepository.markTop(postId);
        integrationEventPublisher.postUpdated(postId);
        postBusinessEventLogger.postTop(actorUserId, postId);
    }

    @Override
    @Transactional
    public void wonderful(UUID actorUserId, UUID postId) {
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        domainService.assertCanModeratePost(actorUserId, post);
        postRepository.markWonderful(postId);
        integrationEventPublisher.postUpdated(postId);
        postBusinessEventLogger.postWonderful(actorUserId, postId);
    }

    @Override
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
        mediaReferenceScheduler.scheduleReleaseForDeletedPost(postId);
        integrationEventPublisher.postDeleted(postId);
    }
}
