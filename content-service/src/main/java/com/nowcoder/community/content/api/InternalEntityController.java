package com.nowcoder.community.content.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.domain.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.internal.dto.EntityResolveResponse;
import com.nowcoder.community.content.dao.CommentMapper;
import com.nowcoder.community.content.dao.DiscussPostMapper;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.nowcoder.community.common.api.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.common.api.ContentErrorCode.POST_NOT_FOUND;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

/**
 * content-service internal entity resolve：用于让下游服务在写路径构造可信 payload（禁止信任客户端注入）。
 *
 * <p>安全：开发阶段内部接口默认放行；生产建议通过网络隔离/网关策略收敛暴露面，并避免对外暴露 /internal/**。</p>
 */
@RestController
@RequestMapping("/internal/content/entities")
public class InternalEntityController {

    private final DiscussPostMapper discussPostMapper;
    private final CommentMapper commentMapper;

    public InternalEntityController(DiscussPostMapper discussPostMapper, CommentMapper commentMapper) {
        this.discussPostMapper = discussPostMapper;
        this.commentMapper = commentMapper;
    }

    /**
     * 解析跨域实体的“事实元信息”（owner/postId）：
     * - POST：entityUserId=post.userId, postId=post.id
     * - COMMENT：entityUserId=comment.userId, postId=comment 所属根 postId
     */
    @GetMapping("/resolve")
    public Result<EntityResolveResponse> resolve(
            @RequestParam int entityType,
            @RequestParam int entityId
    ) {
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
}
