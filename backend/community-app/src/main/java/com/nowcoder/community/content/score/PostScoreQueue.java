package com.nowcoder.community.content.score;

public interface PostScoreQueue {

    void add(int postId);

    Integer pop();

    /**
     * Failure path re-enqueue. Implementations may apply backoff/delay to avoid tight retry loops.
     */
    default void reenqueue(int postId) {
        add(postId);
    }

    /**
     * Success hook to reset any failure/backoff state.
     */
    default void onSuccess(int postId) {
        // no-op
    }
}
