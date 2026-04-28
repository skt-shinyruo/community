package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.ModerationTarget;
import com.nowcoder.community.content.domain.model.ReportSnapshot;
import com.nowcoder.community.content.domain.repository.ModerationTargetRepository;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import com.nowcoder.community.content.infrastructure.persistence.mapper.DiscussPostMapper;
import org.springframework.stereotype.Repository;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;

@Repository
public class MyBatisModerationTargetRepository implements ModerationTargetRepository {

    private final DiscussPostMapper discussPostMapper;
    private final CommentMapper commentMapper;

    public MyBatisModerationTargetRepository(DiscussPostMapper discussPostMapper, CommentMapper commentMapper) {
        this.discussPostMapper = discussPostMapper;
        this.commentMapper = commentMapper;
    }

    @Override
    public ModerationTarget resolveTarget(ReportSnapshot report) {
        if (report == null || report.targetId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "targetId 非法");
        }
        if (report.targetType() == EntityTypes.POST) {
            DiscussPost post = discussPostMapper.selectDiscussPostById(report.targetId());
            if (post == null || post.getStatus() == 2) {
                throw new BusinessException(POST_NOT_FOUND);
            }
            return new ModerationTarget(report.targetType(), report.targetId(), post.getUserId());
        }
        if (report.targetType() == EntityTypes.COMMENT) {
            Comment comment = commentMapper.selectCommentById(report.targetId());
            if (comment == null || comment.getStatus() != 0) {
                throw new BusinessException(COMMENT_NOT_FOUND);
            }
            return new ModerationTarget(report.targetType(), report.targetId(), comment.getUserId());
        }
        if (report.targetType() == EntityTypes.USER) {
            return new ModerationTarget(report.targetType(), report.targetId(), report.targetId());
        }
        throw new BusinessException(INVALID_ARGUMENT, "targetType 非法");
    }
}
