package com.nowcoder.community.content.domain.event;

public interface CommentDomainEventPublisher {

    void commentCreated(CommentCreatedDomainEvent event);
}
