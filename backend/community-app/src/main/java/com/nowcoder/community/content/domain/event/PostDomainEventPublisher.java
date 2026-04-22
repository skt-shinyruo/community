package com.nowcoder.community.content.domain.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * 帖子领域事件发布器：强制要求处于事务内，避免出现“业务已更新但本地事件无法纳入同一事务语义”的隐性一致性窗口。
 */
@Component
public class PostDomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public PostDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void postPublished(UUID postId) {
        assertInTransaction("PostPublished", postId);
        publisher.publishEvent(new PostPublishedDomainEvent(postId));
    }

    public void postUpdated(UUID postId) {
        assertInTransaction("PostUpdated", postId);
        publisher.publishEvent(new PostUpdatedDomainEvent(postId));
    }

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
