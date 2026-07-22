package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.Comment;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CommentContentRepository {

    List<Comment> listRootComments(UUID postId, int page, int size);

    List<Comment> listRootComments(UUID postId, int page, int size, int limit);

    List<Comment> listRootCommentsAfter(UUID postId, Date boundaryTime, UUID boundaryId, int limit);

    List<Comment> listReplies(UUID rootCommentId, int page, int size);

    List<Comment> listReplies(UUID rootCommentId, int page, int size, int limit);

    List<Comment> listRepliesAfter(UUID rootCommentId, Date boundaryTime, UUID boundaryId, int limit);

    List<Comment> listRecentCommentsByUser(UUID userId, int page, int size);

    Comment getById(UUID commentId);

    Comment getByIdAllowDeleted(UUID commentId);

    void assertCommentBelongsToPost(UUID postId, UUID commentId);

    Map<UUID, Comment> getLatestPostActivitiesByPostIds(List<UUID> postIds);
}
