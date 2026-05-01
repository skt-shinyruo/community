package com.nowcoder.community.content.infrastructure.moderation;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.ContentModerationGateway;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.domain.model.ModerationTarget;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.infrastructure.event.ContentEventPublisher;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import com.nowcoder.community.content.infrastructure.persistence.mapper.DiscussPostMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Component
public class MyBatisContentModerationAdapter implements ContentModerationGateway {

    private final DiscussPostMapper discussPostMapper;
    private final CommentMapper commentMapper;
    private final ContentEventPublisher contentEventPublisher;

    public MyBatisContentModerationAdapter(
            DiscussPostMapper discussPostMapper,
            CommentMapper commentMapper,
            ContentEventPublisher contentEventPublisher
    ) {
        this.discussPostMapper = discussPostMapper;
        this.commentMapper = commentMapper;
        this.contentEventPublisher = contentEventPublisher;
    }

    @Override
    public void applyContentAction(UUID actorId, ModerationTarget target, String action, String reason) {
        if (target.targetType() == EntityTypes.POST) {
            int updated = discussPostMapper.updateModerationDeleteMeta(
                    target.targetId(),
                    2,
                    actorId,
                    buildDeletedReason(action, reason),
                    new Date()
            );
            if (updated <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "帖子状态更新失败");
            }
            publishPostDeletedEvent(target.targetId());
            return;
        }
        if (target.targetType() == EntityTypes.COMMENT) {
            int updated = commentMapper.updateModerationDeleteMeta(
                    target.targetId(),
                    1,
                    actorId,
                    buildDeletedReason(action, reason),
                    new Date()
            );
            if (updated <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "评论状态更新失败");
            }
            publishCommentDeletedEvent(target.targetId());
            return;
        }
        throw new BusinessException(FORBIDDEN, "该目标类型不支持此处置动作");
    }

    private void publishPostDeletedEvent(UUID postId) {
        if (postId == null) {
            return;
        }
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null || post.getId() == null) {
            return;
        }
        PostPayload payload = new PostPayload();
        payload.setPostId(post.getId());
        payload.setUserId(post.getUserId());
        payload.setCategoryId(post.getCategoryId());
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setCreateTime(Instant.now());
        payload.setScore(post.getScore());
        contentEventPublisher.publishPostDeleted(payload);
    }

    private void publishCommentDeletedEvent(UUID commentId) {
        if (commentId == null) {
            return;
        }
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null || comment.getId() == null) {
            return;
        }
        UUID postId = resolveRootPostIdByComment(comment, 12);
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(comment.getId());
        payload.setPostId(postId);
        payload.setUserId(comment.getUserId());
        payload.setEntityType(comment.getEntityType());
        payload.setEntityId(comment.getEntityId());
        payload.setCreateTime(Instant.now());
        contentEventPublisher.publishCommentDeleted(payload);
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

    private String buildDeletedReason(String action, String reason) {
        String normalizedAction = action == null ? "" : action.trim().toLowerCase();
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            return normalizedAction;
        }
        if (normalizedReason.length() > 180) {
            normalizedReason = normalizedReason.substring(0, 180);
        }
        return normalizedAction + ": " + normalizedReason;
    }
}
