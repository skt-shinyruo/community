package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import com.nowcoder.community.common.pagination.Pagination;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;

@Service
public class MyBatisCommentContentRepository implements CommentContentRepository {

    private static final int MAX_PAGE_SIZE = 50;

    private final CommentMapper commentMapper;
    private final PostContentRepository postContentPort;

    public MyBatisCommentContentRepository(CommentMapper commentMapper, PostContentRepository postContentPort) {
        this.commentMapper = commentMapper;
        this.postContentPort = postContentPort;
    }

    @Override
    public List<Comment> listRootComments(UUID postId, int page, int size) {
        return listRootComments(postId, page, size, normalizePageSize(size));
    }

    @Override
    public List<Comment> listRootComments(UUID postId, int page, int size, int limit) {
        if (postId == null) {
            return List.of();
        }
        postContentPort.getById(postId);
        int p = Math.max(0, page);
        int s = normalizePageSize(size);
        int fetchLimit = normalizeFetchLimit(limit, s);
        return toAggregates(commentMapper.selectRootComments(postId, Pagination.safeOffset(p, s), fetchLimit));
    }

    @Override
    public List<Comment> listRootCommentsAfter(UUID postId, Date boundaryTime, UUID boundaryId, int limit) {
        if (postId == null) {
            return List.of();
        }
        validateBoundaryPair(boundaryTime, boundaryId);
        postContentPort.getById(postId);
        return toAggregates(commentMapper.selectRootCommentsAfter(
                postId,
                boundaryTime,
                boundaryId,
                normalizeKeysetFetchLimit(limit)
        ));
    }

    @Override
    public List<Comment> listReplies(UUID rootCommentId, int page, int size) {
        return listReplies(rootCommentId, page, size, normalizePageSize(size));
    }

    @Override
    public List<Comment> listReplies(UUID rootCommentId, int page, int size, int limit) {
        if (rootCommentId == null) {
            return List.of();
        }
        int p = Math.max(0, page);
        int s = normalizePageSize(size);
        int fetchLimit = normalizeFetchLimit(limit, s);
        return toAggregates(commentMapper.selectRepliesByRootComment(rootCommentId, Pagination.safeOffset(p, s), fetchLimit));
    }

    @Override
    public List<Comment> listRepliesAfter(UUID rootCommentId, Date boundaryTime, UUID boundaryId, int limit) {
        if (rootCommentId == null) {
            return List.of();
        }
        validateBoundaryPair(boundaryTime, boundaryId);
        return toAggregates(commentMapper.selectRepliesAfter(
                rootCommentId,
                boundaryTime,
                boundaryId,
                normalizeKeysetFetchLimit(limit)
        ));
    }

    @Override
    public List<Comment> listRecentCommentsByUser(UUID userId, int page, int size) {
        if (userId == null) {
            return List.of();
        }
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return toAggregates(commentMapper.selectRecentCommentsByUser(userId, Pagination.safeOffset(p, s), s));
    }

    @Override
    public Comment getById(UUID commentId) {
        CommentDataObject row = commentMapper.selectById(commentId);
        if (row == null || row.getStatus() != 0) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        return CommentPersistenceConverter.toAggregate(row);
    }

    @Override
    public Comment getByIdAllowDeleted(UUID commentId) {
        CommentDataObject row = commentMapper.selectById(commentId);
        if (row == null) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        return CommentPersistenceConverter.toAggregate(row);
    }

    @Override
    public void assertCommentBelongsToPost(UUID postId, UUID commentId) {
        if (postId == null || commentId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "postId/commentId 非法");
        }
        postContentPort.getById(postId);
        int count = commentMapper.existsRootComment(postId, commentId);
        if (count <= 0) {
            throw new BusinessException(NOT_FOUND, "资源不存在");
        }
    }

    @Override
    public Map<UUID, Comment> getLatestPostActivitiesByPostIds(List<UUID> postIds) {
        Map<UUID, Comment> map = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return map;
        }

        List<CommentDataObject> rows = commentMapper.selectLatestPostActivitiesByPostIds(postIds);
        if (rows == null || rows.isEmpty()) {
            return map;
        }

        for (CommentDataObject row : rows) {
            if (row == null || row.getPostId() == null) {
                continue;
            }
            Comment comment = CommentPersistenceConverter.toAggregate(row);
            map.putIfAbsent(comment.getPostId(), comment);
        }
        return map;
    }

    private static List<Comment> toAggregates(List<CommentDataObject> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(CommentPersistenceConverter::toAggregate)
                .toList();
    }

    private static int normalizePageSize(int size) {
        return Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }

    private static int normalizeFetchLimit(int limit, int pageSize) {
        return Math.min(pageSize + 1, Math.max(1, limit));
    }

    private static int normalizeKeysetFetchLimit(int limit) {
        return Math.min(MAX_PAGE_SIZE + 1, Math.max(1, limit));
    }

    private static void validateBoundaryPair(Date boundaryTime, UUID boundaryId) {
        if ((boundaryTime == null) != (boundaryId == null)) {
            throw new BusinessException(INVALID_ARGUMENT, "评论游标边界非法");
        }
    }
}
