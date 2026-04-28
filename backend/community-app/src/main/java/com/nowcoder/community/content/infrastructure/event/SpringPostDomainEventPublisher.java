package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.domain.event.PostDeletedDomainEvent;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.domain.event.PostPublishedDomainEvent;
import com.nowcoder.community.content.domain.event.PostUpdatedDomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Component
public class SpringPostDomainEventPublisher implements PostDomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringPostDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void postPublished(UUID postId) {
        assertInTransaction("PostPublished", postId);
        publisher.publishEvent(new PostPublishedDomainEvent(postId));
    }

    @Override
    public void postUpdated(UUID postId) {
        assertInTransaction("PostUpdated", postId);
        publisher.publishEvent(new PostUpdatedDomainEvent(postId));
    }

    @Override
    public void postDeleted(UUID postId) {
        assertInTransaction("PostDeleted", postId);
        publisher.publishEvent(new PostDeletedDomainEvent(postId));
    }

    private void assertInTransaction(String name, UUID postId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("发布领域事件失败：当前不在事务中（" + name + ", postId=" + postId + "）");
        }
    }
}
