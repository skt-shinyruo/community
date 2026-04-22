package com.nowcoder.community.content.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.api.query.ContentEntityQueryApi;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.mapper.CommentMapper;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;

@Service
public class ContentEntityQueryService implements ContentEntityQueryApi {

    private final DiscussPostMapper discussPostMapper;
    private final CommentMapper commentMapper;

    public ContentEntityQueryService(DiscussPostMapper discussPostMapper, CommentMapper commentMapper) {
        this.discussPostMapper = discussPostMapper;
        this.commentMapper = commentMapper;
    }

    @Override
    public ResolvedContentRef resolve(int entityType, UUID entityId) {
        if (entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "entityId 非法");
        }
        if (entityType == EntityTypes.POST) {
            return resolvePost(entityId);
        }
        if (entityType == EntityTypes.COMMENT) {
            return resolveComment(entityId);
        }
        throw new BusinessException(INVALID_ARGUMENT, "仅支持解析 POST/COMMENT");
    }

    private ResolvedContentRef resolvePost(UUID postId) {
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null || post.getStatus() == 2) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        return new ResolvedContentRef(post.getUserId(), post.getId());
    }

    private ResolvedContentRef resolveComment(UUID commentId) {
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null || comment.getStatus() != 0) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        UUID postId = resolveRootPostIdByComment(comment, 12);
        if (postId == null) {
            throw new BusinessException(POST_NOT_FOUND, "评论所属帖子不存在");
        }
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null || post.getStatus() == 2) {
            throw new BusinessException(POST_NOT_FOUND, "评论所属帖子不存在");
        }
        return new ResolvedContentRef(comment.getUserId(), postId);
    }

    private UUID resolveRootPostIdByComment(Comment comment, int maxHops) {
        if (comment == null || comment.getId() == null) {
            return null;
        }
        int type = comment.getEntityType();
        UUID id = comment.getEntityId();
        for (int i = 0; i < Math.max(1, maxHops); i++) {
            if (type == EntityTypes.POST) {
                return id;
            }
            if (type != EntityTypes.COMMENT || id == null) {
                return null;
            }
            Comment parent = commentMapper.selectCommentById(id);
            if (parent == null || parent.getStatus() != 0) {
                return null;
            }
            type = parent.getEntityType();
            id = parent.getEntityId();
        }
        return null;
    }
}
