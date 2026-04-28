package com.nowcoder.community.content.application.port;

import java.util.UUID;

public interface PostScoreQueuePort {

    void add(UUID postId);

    UUID pop();

    /**
     * Failure path re-enqueue. Implementations may apply backoff/delay to avoid tight retry loops.
     */
    default void reenqueue(UUID postId) {
        add(postId);
    }

    /**
     * Success hook to reset any failure/backoff state.
     */
    default void onSuccess(UUID postId) {
        // no-op
    }
}
