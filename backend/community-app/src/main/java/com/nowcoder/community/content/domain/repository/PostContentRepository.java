package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.DiscussPost;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface PostContentRepository {

    int ORDER_LATEST = 0;
    int ORDER_HOT = 1;

    List<DiscussPost> listPosts(int page, int size, int orderMode);

    List<DiscussPost> listPosts(int page, int size, int orderMode, UUID categoryId, String tag);

    List<DiscussPost> listPostsByUser(UUID userId, int page, int size);

    List<DiscussPost> listPostsByIds(List<UUID> postIds);

    DiscussPost getById(UUID postId);

    DiscussPost getByIdAllowDeleted(UUID postId);

    List<DiscussPost> listSubscribedPosts(
            UUID userId,
            List<UUID> subscribedCategoryIds,
            int page,
            int size,
            int orderMode,
            UUID categoryId,
            String tag
    );

    UUID create(DiscussPost post);

    void updateCommentCount(UUID postId, int commentCount);

    void incrementCommentCount(UUID postId, int delta);

    void updateType(UUID postId, int type);

    void updateStatus(UUID postId, int status);

    void updateScore(UUID postId, double score);

    void updatePostContent(UUID postId, String title, String content, UUID categoryId, Date updateTime);

    void updateModerationDeleteMeta(UUID postId, int status, UUID deletedBy, String deletedReason, Date deletedTime);
}
