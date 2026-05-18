package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;

@Service
public class MyBatisCommentContentRepository implements CommentContentRepository {

    public static final int ENTITY_TYPE_POST = EntityTypes.POST;
    public static final int ENTITY_TYPE_COMMENT = EntityTypes.COMMENT;

    private final CommentMapper commentMapper;
    private final PostContentRepository postContentPort;

    public MyBatisCommentContentRepository(CommentMapper commentMapper, PostContentRepository postContentPort) {
        this.commentMapper = commentMapper;
        this.postContentPort = postContentPort;
    }

    @Override
    public List<Comment> listByPost(UUID postId, int page, int size) {
        if (postId == null) {
            return List.of();
        }
        postContentPort.getById(postId);
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return commentMapper.selectCommentsByEntity(ENTITY_TYPE_POST, postId, Pagination.safeOffset(p, s), s);
    }

    @Override
    public List<Comment> listReplies(UUID commentId, int page, int size) {
        if (commentId == null) {
            return List.of();
        }
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return commentMapper.selectCommentsByEntity(ENTITY_TYPE_COMMENT, commentId, Pagination.safeOffset(p, s), s);
    }

    @Override
    public List<Comment> listRecentCommentsByUser(UUID userId, int page, int size) {
        if (userId == null) {
            return List.of();
        }
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return commentMapper.selectRecentCommentsByUser(userId, Pagination.safeOffset(p, s), s);
    }

    @Override
    public Comment getById(UUID commentId) {
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null || !comment.isActive()) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        return comment;
    }

    @Override
    public Comment getByIdAllowDeleted(UUID commentId) {
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        return comment;
    }

    @Override
    public void assertCommentBelongsToPost(UUID postId, UUID commentId) {
        if (postId == null || commentId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "postId/commentId 非法");
        }
        postContentPort.getById(postId);
        int count = commentMapper.existsPostComment(postId, commentId);
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

        List<Comment> rows = commentMapper.selectLatestPostActivitiesByPostIds(postIds);
        if (rows == null || rows.isEmpty()) {
            return map;
        }

        for (Comment comment : rows) {
            if (comment == null || comment.getEntityId() == null) {
                continue;
            }
            map.putIfAbsent(comment.getEntityId(), comment);
        }
        return map;
    }
}
