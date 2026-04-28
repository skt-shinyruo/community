package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.port.PostContentPort;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

/**
 * 热帖分数写路径：将“score 更新 + PostUpdated 事件”收敛到同一事务域，
 * 避免分数更新与后续本地投影之间出现一致性裂缝。
 */
@Service
public class PostScoreUpdateApplicationService {

    private final PostContentPort postContentPort;
    private final PostDomainEventPublisher domainEventPublisher;

    public PostScoreUpdateApplicationService(PostContentPort postContentPort, PostDomainEventPublisher domainEventPublisher) {
        this.postContentPort = postContentPort;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public void updateScore(UUID postId, double score) {
        if (postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "postId 非法");
        }
        postContentPort.updateScore(postId, score);
        domainEventPublisher.postUpdated(postId);
    }
}
