package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.PostContractEventApplicationService;
import com.nowcoder.community.content.domain.event.PostDeletedDomainEvent;
import com.nowcoder.community.content.domain.event.PostPublishedDomainEvent;
import com.nowcoder.community.content.domain.event.PostUpdatedDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PostDomainEventBridge {

    private final PostContractEventApplicationService applicationService;

    public PostDomainEventBridge(PostContractEventApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onPostPublished(PostPublishedDomainEvent event) {
        applicationService.publishPostPublished(event.postId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onPostUpdated(PostUpdatedDomainEvent event) {
        applicationService.publishPostUpdated(event.postId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onPostDeleted(PostDeletedDomainEvent event) {
        applicationService.publishPostDeleted(event.postId());
    }
}
