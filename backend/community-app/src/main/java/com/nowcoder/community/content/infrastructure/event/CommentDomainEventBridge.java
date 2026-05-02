package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.ContentEventPublisher;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CommentDomainEventBridge {

    private final ContentEventPublisher eventPublisher;

    public CommentDomainEventBridge(ContentEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onCommentCreated(CommentCreatedDomainEvent event) {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(event.commentId());
        payload.setPostId(event.postId());
        payload.setUserId(event.userId());
        payload.setEntityType(event.entityType());
        payload.setEntityId(event.entityId());
        payload.setTargetUserId(event.targetUserId());
        payload.setContent(event.content());
        payload.setCreateTime(event.createTime());
        eventPublisher.publishCommentCreated(payload);
    }
}
