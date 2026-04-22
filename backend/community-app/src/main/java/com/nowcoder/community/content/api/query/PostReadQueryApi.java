package com.nowcoder.community.content.api.query;

import com.nowcoder.community.content.api.model.PostDetailView;
import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;

import java.util.List;
import java.util.UUID;

public interface PostReadQueryApi {

    List<PostSummaryView> listPosts(UUID currentUserId, String order, UUID categoryId, String tag, Boolean subscribed, Integer page, Integer size);

    List<PostSummaryView> listPostsByUser(UUID userId, Integer page, Integer size);

    List<PostSummaryView> listPostsByIds(List<UUID> postIds);

    PostDetailView getPostDetail(UUID currentUserId, UUID postId);

    List<RecentUserCommentView> listRecentCommentsByUser(UUID userId, Integer page, Integer size);
}
