package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.service.PostModerationDomainService;
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
    private final PostBusinessEventLogger postBusinessEventLogger;

    public PostModerationApplicationService(
            PostModerationDomainService domainService,
            PostRepository postRepository,
            PostDomainEventPublisher domainEventPublisher,
            PostWriteSideEffectScheduler postWriteSideEffectScheduler,
            PostBusinessEventLogger postBusinessEventLogger
    ) {
        this.domainService = domainService;
        this.postRepository = postRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.postWriteSideEffectScheduler = postWriteSideEffectScheduler;
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
        postRepository.markDeletedByAdmin(postId, actorUserId, new Date());
        domainEventPublisher.postDeleted(postId);
        postBusinessEventLogger.postDeleteByAdmin(actorUserId, postId);
    }
}
