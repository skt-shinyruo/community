package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.DiscussPost;

import java.util.List;
import java.util.UUID;

public interface BookmarkRepository {

    boolean add(UUID userId, UUID postId);

    boolean remove(UUID userId, UUID postId);

    boolean hasBookmarked(UUID userId, UUID postId);

    List<DiscussPost> listBookmarkedPosts(UUID userId, int page, int size);
}
