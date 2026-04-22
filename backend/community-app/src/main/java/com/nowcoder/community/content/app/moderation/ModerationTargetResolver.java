package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.content.mapper.CommentMapper;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import com.nowcoder.community.content.service.ReportService;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;

@Component
public class ModerationTargetResolver {

    private final DiscussPostMapper discussPostMapper;
    private final CommentMapper commentMapper;

    public ModerationTargetResolver(DiscussPostMapper discussPostMapper, CommentMapper commentMapper) {
        this.discussPostMapper = discussPostMapper;
        this.commentMapper = commentMapper;
    }

    public ResolvedTarget resolveTarget(Report report) {
        int type = report.getTargetType();
        UUID targetId = report.getTargetId();

        if (type == ReportService.TARGET_TYPE_POST) {
            DiscussPost post = discussPostMapper.selectDiscussPostById(targetId);
            if (post == null || post.getStatus() == 2) {
                throw new BusinessException(POST_NOT_FOUND);
            }
            return new ResolvedTarget(type, targetId, post.getUserId());
        }

        if (type == ReportService.TARGET_TYPE_COMMENT) {
            Comment comment = commentMapper.selectCommentById(targetId);
            if (comment == null || comment.getStatus() != 0) {
                throw new BusinessException(COMMENT_NOT_FOUND);
            }
            return new ResolvedTarget(type, targetId, comment.getUserId());
        }

        if (type == ReportService.TARGET_TYPE_USER) {
            return new ResolvedTarget(type, targetId, targetId);
        }

        throw new BusinessException(INVALID_ARGUMENT, "targetType 非法");
    }

    public record ResolvedTarget(int targetType, UUID targetId, UUID targetUserId) {
    }
}
