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

    List<DiscussPost> listRecentVisiblePostsByAuthorIds(List<UUID> authorIds, int limit);

    List<DiscussPost> listRecentVisiblePostsByAuthorIdsBefore(List<UUID> authorIds, Date beforeCreateTime, UUID beforePostId, int limit);

    List<DiscussPost> scanAfterId(UUID afterId, int limit);

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

    /**
     * Applies a comment-derived mutation only while the post is active and returns the new aggregate version.
     * A non-positive result means the post no longer accepted the mutation.
     */
    long incrementActiveCommentCount(UUID postId, int delta);

    /**
     * Updates the derived score while the owning Post aggregate version is unchanged and returns the new score version.
     */
    long updateScore(UUID postId, double score, long expectedVersion);
}
