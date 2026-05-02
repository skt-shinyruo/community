package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDeletedDomainEvent;
import org.springframework.stereotype.Service;

@Service
public class CommentContractEventApplicationService {

    private final ContentEventPublisher eventPublisher;

    public CommentContractEventApplicationService(ContentEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishCommentCreated(CommentCreatedDomainEvent event) {
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

    public void publishCommentDeleted(CommentDeletedDomainEvent event) {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(event.commentId());
        payload.setPostId(event.postId());
        payload.setUserId(event.userId());
        payload.setEntityType(event.entityType());
        payload.setEntityId(event.entityId());
        payload.setCreateTime(event.createTime());
        eventPublisher.publishCommentDeleted(payload);
    }
}
