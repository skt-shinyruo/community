package com.nowcoder.community.content.score;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.service.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

/**
 * 热帖分数写路径命令服务：将“score 更新 + PostUpdated 事件”收敛到同一事务域，
 * 避免分数更新与后续本地投影之间出现一致性裂缝。
 */
@Service
public class PostScoreCommandService {

    private final PostService postService;
    private final PostDomainEventPublisher domainEventPublisher;

    public PostScoreCommandService(PostService postService, PostDomainEventPublisher domainEventPublisher) {
        this.postService = postService;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public void updateScore(int postId, double score) {
        int pid = Math.max(0, postId);
        if (pid <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "postId 非法");
        }
        postService.updateScore(pid, score);
        domainEventPublisher.postUpdated(pid);
    }
}
