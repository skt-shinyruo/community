package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostDetailResult;

import java.util.UUID;

public interface PostDetailCache {

    PostDetailResult get(UUID postId);

    void put(UUID postId, PostDetailResult detail);

    default void put(UUID postId, PostDetailResult detail, long sourceVersion) {
        put(postId, detail);
    }

    void evict(UUID postId);

    default void evict(UUID postId, long minimumVersion) {
        evict(postId);
    }

    void terminalEvict(UUID postId);

    default void terminalEvict(UUID postId, long minimumVersion) {
        terminalEvict(postId);
    }
}
