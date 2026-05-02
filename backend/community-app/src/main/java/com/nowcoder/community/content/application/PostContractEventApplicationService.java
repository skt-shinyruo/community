package com.nowcoder.community.content.application;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostContractEventApplicationService {

    private final ContentPostPayloadAssembler postPayloadAssembler;
    private final ContentEventPublisher eventPublisher;

    public PostContractEventApplicationService(
            ContentPostPayloadAssembler postPayloadAssembler,
            ContentEventPublisher eventPublisher
    ) {
        this.postPayloadAssembler = postPayloadAssembler;
        this.eventPublisher = eventPublisher;
    }

    public void publishPostPublished(UUID postId) {
        eventPublisher.publishPostPublished(postPayloadAssembler.assemble(postId));
    }

    public void publishPostUpdated(UUID postId) {
        eventPublisher.publishPostUpdated(postPayloadAssembler.assemble(postId));
    }

    public void publishPostDeleted(UUID postId) {
        eventPublisher.publishPostDeleted(postPayloadAssembler.assemble(postId));
    }
}
