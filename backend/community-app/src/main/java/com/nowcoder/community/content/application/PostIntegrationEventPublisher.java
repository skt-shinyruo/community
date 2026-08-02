package com.nowcoder.community.content.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class PostIntegrationEventPublisher {

    private final ContentPostPayloadAssembler postPayloadAssembler;
    private final ContentEventPublisher eventPublisher;
    private final PostCacheAfterCommit postCacheAfterCommit;

    public PostIntegrationEventPublisher(
            ContentPostPayloadAssembler postPayloadAssembler,
            ContentEventPublisher eventPublisher,
            PostCacheAfterCommit postCacheAfterCommit
    ) {
        this.postPayloadAssembler = postPayloadAssembler;
        this.eventPublisher = eventPublisher;
        this.postCacheAfterCommit = postCacheAfterCommit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void postPublished(UUID postId) {
        eventPublisher.publishPostPublished(postPayloadAssembler.assemble(postId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void postUpdated(UUID postId) {
        var payload = postPayloadAssembler.assemble(postId);
        eventPublisher.publishPostUpdated(payload);
        postCacheAfterCommit.evict(postId, payload.getAggregateVersion());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void postDeleted(UUID postId) {
        var payload = postPayloadAssembler.assemble(postId);
        eventPublisher.publishPostDeleted(payload);
        postCacheAfterCommit.terminalEvict(postId, payload.getCategoryId(), payload.getAggregateVersion());
    }
}
