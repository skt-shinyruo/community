package com.nowcoder.community.content.app.post;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.service.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class TopPostUseCase {

    private final PostService postService;
    private final PostDomainEventPublisher domainEventPublisher;

    public TopPostUseCase(PostService postService, PostDomainEventPublisher domainEventPublisher) {
        this.postService = postService;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public void topPost(UUID actorUserId, UUID postId) {
        if (actorUserId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        postService.getById(postId);
        postService.updateType(postId, 1);
        domainEventPublisher.postUpdated(postId);
    }
}
