package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class SpringCommentDomainEventPublisher implements CommentDomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringCommentDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void commentCreated(CommentCreatedDomainEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("发布领域事件失败：当前不在事务中（CommentCreated, commentId=" + event.commentId() + "）");
        }
        publisher.publishEvent(event);
    }
}
