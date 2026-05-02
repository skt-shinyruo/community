package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.CommentContractEventApplicationService;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDeletedDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CommentDomainEventBridge {

    private final CommentContractEventApplicationService applicationService;

    public CommentDomainEventBridge(CommentContractEventApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onCommentCreated(CommentCreatedDomainEvent event) {
        applicationService.publishCommentCreated(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onCommentDeleted(CommentDeletedDomainEvent event) {
        applicationService.publishCommentDeleted(event);
    }
}
