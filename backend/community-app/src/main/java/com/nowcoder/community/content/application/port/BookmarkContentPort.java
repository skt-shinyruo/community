package com.nowcoder.community.content.application.port;

import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.DiscussPost;

import java.util.List;
import java.util.UUID;

public interface BookmarkContentPort {

    void add(UUID userId, UUID postId);

    void remove(UUID userId, UUID postId);

    boolean hasBookmarked(UUID userId, UUID postId);

    List<DiscussPost> listBookmarkedPosts(UUID userId, int page, int size);

    List<PostSummaryResult> listBookmarkedPostSummaries(UUID userId, int page, int size);
}
