package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostDetailResult;

import java.util.UUID;

public interface PostDetailCache {

    PostDetailResult get(UUID postId);

    void put(UUID postId, PostDetailResult detail);

    void evict(UUID postId);

    void terminalEvict(UUID postId);
}
