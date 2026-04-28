package com.nowcoder.community.content.domain.event;

import java.util.UUID;

public interface PostDomainEventPublisher {

    void postPublished(UUID postId);

    void postUpdated(UUID postId);

    void postDeleted(UUID postId);
}
