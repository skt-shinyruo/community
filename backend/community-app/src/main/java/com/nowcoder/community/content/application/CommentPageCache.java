package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.CommentPageResult;

import java.util.UUID;

public interface CommentPageCache {

    CommentPageResult getRootPage(UUID postId, String cursor, int size);

    void putRootPage(UUID postId, String cursor, int size, CommentPageResult result);

    void evictPost(UUID postId);
}
