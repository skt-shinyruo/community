package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.service.PostModerationDomainService;
import com.nowcoder.community.user.api.action.UserModerationActionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Service
public class PostModerationApplicationService {

    private final PostModerationDomainService domainService;
    private final PostRepository postRepository;
    private final PostIntegrationEventPublisher integrationEventPublisher;
    private final PostMediaReferenceScheduler mediaReferenceScheduler;
    private final PostBusinessEventLogger postBusinessEventLogger;
    private final UserModerationActionApi userModerationActionApi;
    private final Clock clock;

    public PostModerationApplicationService(
            PostModerationDomainService domainService,
            PostRepository postRepository,
            PostIntegrationEventPublisher integrationEventPublisher,
            PostMediaReferenceScheduler mediaReferenceScheduler,
            PostBusinessEventLogger postBusinessEventLogger,
            UserModerationActionApi userModerationActionApi,
            Clock clock
    ) {
        this.domainService = domainService;
        this.postRepository = postRepository;
        this.integrationEventPublisher = integrationEventPublisher;
        this.mediaReferenceScheduler = mediaReferenceScheduler;
        this.postBusinessEventLogger = postBusinessEventLogger;
        this.userModerationActionApi = userModerationActionApi;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public void top(UUID actorUserId, UUID postId) {
        userModerationActionApi.assertActiveModerationActor(actorUserId);
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        domainService.assertCanModeratePost(actorUserId, post);
        postRepository.markTop(postId, Date.from(clock.instant()), post.aggregateVersion());
        integrationEventPublisher.postUpdated(postId);
        postBusinessEventLogger.postTop(actorUserId, postId);
    }

    @Transactional
    public void wonderful(UUID actorUserId, UUID postId) {
        userModerationActionApi.assertActiveModerationActor(actorUserId);
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        domainService.assertCanModeratePost(actorUserId, post);
        postRepository.markWonderful(postId, Date.from(clock.instant()), post.aggregateVersion());
        integrationEventPublisher.postUpdated(postId);
        postBusinessEventLogger.postWonderful(actorUserId, postId);
    }

    @Transactional
    public void delete(UUID actorUserId, UUID postId) {
        userModerationActionApi.assertActiveModerationActor(actorUserId);
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        if (!domainService.shouldAdminDelete(actorUserId, post)) {
            return;
        }
        boolean changed = postRepository.markDeletedByAdmin(
                postId,
                actorUserId,
                Date.from(clock.instant()),
                post.aggregateVersion()
        );
        if (!changed) {
            return;
        }
        applyPostDeleteSideEffects(postId);
        postBusinessEventLogger.postDeleteByAdmin(actorUserId, postId);
    }

    @Transactional
    public void deleteByModeration(UUID actorUserId, UUID postId) {
        userModerationActionApi.assertActiveModerationActor(actorUserId);
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        boolean changed = postRepository.markDeletedByAdmin(
                postId,
                actorUserId,
                Date.from(clock.instant()),
                post.aggregateVersion()
        );
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
