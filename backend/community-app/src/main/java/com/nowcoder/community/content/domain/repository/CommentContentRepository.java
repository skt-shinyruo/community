package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.Comment;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CommentContentRepository {

    int ROOT_ORDER_LATEST = 0;
    int ROOT_ORDER_EARLIEST = 1;

    List<Comment> listRootCommentsAfter(
            UUID postId,
            Date boundaryTime,
            UUID boundaryId,
            int orderDirection,
            int limit
    );

    List<Comment> listRepliesAfter(UUID rootCommentId, Date boundaryTime, UUID boundaryId, int limit);

    List<Comment> listRecentCommentsByUser(UUID userId, int page, int size);

    Comment getById(UUID commentId);

    Comment getByIdAllowDeleted(UUID commentId);

    void assertCommentBelongsToPost(UUID postId, UUID commentId);

    Map<UUID, Comment> getLatestPostActivitiesByPostIds(List<UUID> postIds);
}
