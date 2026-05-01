package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.Comment;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CommentContentRepository {

    int ENTITY_TYPE_POST = 1;
    int ENTITY_TYPE_COMMENT = 2;

    List<Comment> listByPost(UUID postId, int page, int size);

    List<Comment> listReplies(UUID commentId, int page, int size);

    List<Comment> listRecentCommentsByUser(UUID userId, int page, int size);

    Comment getById(UUID commentId);

    void assertCommentBelongsToPost(UUID postId, UUID commentId);

    Map<UUID, Comment> getLatestPostActivitiesByPostIds(List<UUID> postIds);
}
