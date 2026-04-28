package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.domain.event.PostDeletedDomainEvent;
import com.nowcoder.community.content.domain.event.PostPublishedDomainEvent;
import com.nowcoder.community.content.domain.event.PostUpdatedDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 领域事件 → 本地内容事件桥接层：
 * - BEFORE_COMMIT：保证事件 payload 基于同一事务内的权威数据构造
 * - 统一构造 payload：避免多处手工拼装导致字段漂移/缺失
 */
@Component
public class PostDomainEventBridge {

    private final ContentEventPublisher eventPublisher;
    private final PostPayloadAssembler postPayloadAssembler;

    public PostDomainEventBridge(ContentEventPublisher eventPublisher, PostPayloadAssembler postPayloadAssembler) {
        this.eventPublisher = eventPublisher;
        this.postPayloadAssembler = postPayloadAssembler;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onPostPublished(PostPublishedDomainEvent event) {
        eventPublisher.publishPostPublished(postPayloadAssembler.assemble(event.postId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onPostUpdated(PostUpdatedDomainEvent event) {
        eventPublisher.publishPostUpdated(postPayloadAssembler.assemble(event.postId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onPostDeleted(PostDeletedDomainEvent event) {
        eventPublisher.publishPostDeleted(postPayloadAssembler.assemble(event.postId()));
    }
}
