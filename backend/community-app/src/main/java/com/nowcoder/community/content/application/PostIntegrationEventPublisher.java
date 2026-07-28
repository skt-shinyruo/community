package com.nowcoder.community.content.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class PostIntegrationEventPublisher {

    private final ContentPostPayloadAssembler postPayloadAssembler;
    private final ContentEventPublisher eventPublisher;

    public PostIntegrationEventPublisher(
            ContentPostPayloadAssembler postPayloadAssembler,
            ContentEventPublisher eventPublisher
    ) {
        this.postPayloadAssembler = postPayloadAssembler;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void postPublished(UUID postId) {
        eventPublisher.publishPostPublished(postPayloadAssembler.assemble(postId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void postUpdated(UUID postId) {
        eventPublisher.publishPostUpdated(postPayloadAssembler.assemble(postId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void postDeleted(UUID postId) {
        eventPublisher.publishPostDeleted(postPayloadAssembler.assemble(postId));
    }
}
