package com.nowcoder.community.content.rpc;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.ErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.domain.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.internal.dto.EntityResolveResponse;
import com.nowcoder.community.content.api.rpc.ContentEntityRpcService;
import com.nowcoder.community.content.dao.CommentMapper;
import com.nowcoder.community.content.dao.DiscussPostMapper;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.common.api.ContentErrorCode.POST_NOT_FOUND;

@DubboService
public class ContentEntityRpcServiceImpl implements ContentEntityRpcService {

    private final DiscussPostMapper discussPostMapper;
    private final CommentMapper commentMapper;

    public ContentEntityRpcServiceImpl(DiscussPostMapper discussPostMapper, CommentMapper commentMapper) {
        this.discussPostMapper = discussPostMapper;
        this.commentMapper = commentMapper;
    }

    @Override
    public Result<EntityResolveResponse> resolveEntity(int entityType, int entityId) {
        try {
            if (entityId <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "entityId 非法");
            }
            if (entityType == EntityTypes.POST) {
                return Result.ok(resolvePost(entityId));
            }
            if (entityType == EntityTypes.COMMENT) {
                return Result.ok(resolveComment(entityId));
            }
            throw new BusinessException(INVALID_ARGUMENT, "仅支持解析 POST/COMMENT");
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private EntityResolveResponse resolvePost(int postId) {
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null || post.getId() <= 0 || post.getStatus() == 2) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        EntityResolveResponse r = new EntityResolveResponse();
        r.setEntityType(EntityTypes.POST);
        r.setEntityId(post.getId());
        r.setEntityUserId(post.getUserId());
        r.setPostId(post.getId());
        return r;
    }

    private EntityResolveResponse resolveComment(int commentId) {
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null || comment.getId() <= 0 || comment.getStatus() != 0) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        int postId = resolveRootPostIdByComment(comment, 12);
        if (postId <= 0) {
            throw new BusinessException(POST_NOT_FOUND, "评论所属帖子不存在");
        }
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null || post.getId() <= 0 || post.getStatus() == 2) {
            throw new BusinessException(POST_NOT_FOUND, "评论所属帖子不存在");
        }

        EntityResolveResponse r = new EntityResolveResponse();
        r.setEntityType(EntityTypes.COMMENT);
        r.setEntityId(comment.getId());
        r.setEntityUserId(comment.getUserId());
        r.setPostId(postId);
        return r;
    }

    private int resolveRootPostIdByComment(Comment comment, int maxHops) {
        if (comment == null || comment.getId() <= 0) {
            return 0;
        }
        int t = comment.getEntityType();
        int id = comment.getEntityId();
        for (int i = 0; i < Math.max(1, maxHops); i++) {
            if (t == EntityTypes.POST) {
                return id;
            }
            if (t != EntityTypes.COMMENT || id <= 0) {
                return 0;
            }
            Comment parent = commentMapper.selectCommentById(id);
            if (parent == null || parent.getId() <= 0 || parent.getStatus() != 0) {
                return 0;
            }
            t = parent.getEntityType();
            id = parent.getEntityId();
        }
        return 0;
    }

    private <T> Result<T> error(BusinessException e) {
        if (e == null) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
        ErrorCode ec = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : e.getErrorCode();
        String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : ec.getMessage();
        return Result.error(ec.getCode(), msg);
    }
}

