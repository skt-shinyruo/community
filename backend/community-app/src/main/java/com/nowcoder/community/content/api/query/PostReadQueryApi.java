package com.nowcoder.community.content.api.query;

import com.nowcoder.community.content.api.model.PostDetailView;
import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;

import java.util.List;

public interface PostReadQueryApi {

    List<PostSummaryView> listPosts(int currentUserId, String order, Integer categoryId, String tag, Boolean subscribed, Integer page, Integer size);

    List<PostSummaryView> listPostsByUser(int userId, Integer page, Integer size);

    List<PostSummaryView> listPostsByIds(List<Integer> postIds);

    PostDetailView getPostDetail(int currentUserId, int postId);

    List<RecentUserCommentView> listRecentCommentsByUser(int userId, Integer page, Integer size);
}
