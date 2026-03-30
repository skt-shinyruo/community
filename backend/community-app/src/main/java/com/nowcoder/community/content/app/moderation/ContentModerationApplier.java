package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.mapper.CommentMapper;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import com.nowcoder.community.content.service.ReportService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Component
public class ContentModerationApplier {

    private final DiscussPostMapper discussPostMapper;
    private final CommentMapper commentMapper;
    private final ContentEventPublisher contentEventPublisher;

    public ContentModerationApplier(
            DiscussPostMapper discussPostMapper,
            CommentMapper commentMapper,
            ContentEventPublisher contentEventPublisher
    ) {
        this.discussPostMapper = discussPostMapper;
        this.commentMapper = commentMapper;
        this.contentEventPublisher = contentEventPublisher;
    }

    public void applyContentAction(int actorId, ModerationTargetResolver.ResolvedTarget target, String action, String reason) {
        if (target.targetType() == ReportService.TARGET_TYPE_POST) {
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

        if (target.targetType() == ReportService.TARGET_TYPE_COMMENT) {
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

    private void publishPostDeletedEvent(int postId) {
        if (postId <= 0) {
            return;
        }
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null || post.getId() <= 0) {
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

    private void publishCommentDeletedEvent(int commentId) {
        if (commentId <= 0) {
            return;
        }
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null || comment.getId() <= 0) {
            return;
        }
        int postId = resolveRootPostIdByComment(comment, 12);
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(comment.getId());
        payload.setPostId(Math.max(0, postId));
        payload.setUserId(comment.getUserId());
        payload.setEntityType(comment.getEntityType());
        payload.setEntityId(comment.getEntityId());
        payload.setCreateTime(Instant.now());
        contentEventPublisher.publishCommentDeleted(payload);
    }

    private int resolveRootPostIdByComment(Comment comment, int maxHops) {
        if (comment == null || comment.getId() <= 0) {
            return 0;
        }
        int type = comment.getEntityType();
        int id = comment.getEntityId();
        for (int i = 0; i < Math.max(1, maxHops); i++) {
            if (type == ReportService.TARGET_TYPE_POST) {
                return id;
            }
            if (type != ReportService.TARGET_TYPE_COMMENT || id <= 0) {
                return 0;
            }
            Comment parent = commentMapper.selectCommentById(id);
            if (parent == null || parent.getId() <= 0 || parent.getStatus() != 0) {
                return 0;
            }
            type = parent.getEntityType();
            id = parent.getEntityId();
        }
        return 0;
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
