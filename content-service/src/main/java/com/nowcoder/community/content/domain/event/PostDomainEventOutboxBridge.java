package com.nowcoder.community.content.domain.event;

import com.nowcoder.community.content.domain.assembler.PostPayloadAssembler;
import com.nowcoder.community.content.event.ContentEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 领域事件 → Outbox/Kafka 桥接层：
 * - BEFORE_COMMIT：保证 outbox enqueue 与业务表更新在同一事务提交
 * - 统一构造 payload：避免多处手工拼装导致字段漂移/缺失
 */
@Component
public class PostDomainEventOutboxBridge {

    private final ContentEventPublisher eventPublisher;
    private final PostPayloadAssembler postPayloadAssembler;

    public PostDomainEventOutboxBridge(ContentEventPublisher eventPublisher, PostPayloadAssembler postPayloadAssembler) {
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

