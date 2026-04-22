package com.nowcoder.community.content.app.post;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.service.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class AdminDeletePostUseCase {

    private final PostService postService;
    private final PostDomainEventPublisher domainEventPublisher;

    public AdminDeletePostUseCase(PostService postService, PostDomainEventPublisher domainEventPublisher) {
        this.postService = postService;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public void adminDelete(UUID actorUserId, UUID postId) {
        if (actorUserId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        DiscussPost existed = postService.getByIdAllowDeleted(postId);
        if (existed.getStatus() == 2) {
            return;
        }
        postService.updateModerationDeleteMeta(postId, 2, actorUserId, "admin_delete", new Date());
        domainEventPublisher.postDeleted(postId);
    }
}
