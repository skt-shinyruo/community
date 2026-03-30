package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.content.mapper.CommentMapper;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import com.nowcoder.community.content.service.ReportService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModerationTargetResolverTest {

    @Test
    void resolveTargetShouldProjectPostOwner() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        ModerationTargetResolver resolver = new ModerationTargetResolver(discussPostMapper, commentMapper);
        DiscussPost post = new DiscussPost();
        post.setId(88);
        post.setUserId(9);
        post.setStatus(0);
        when(discussPostMapper.selectDiscussPostById(88)).thenReturn(post);

        ModerationTargetResolver.ResolvedTarget target = resolver.resolveTarget(report(12, ReportService.TARGET_TYPE_POST, 88));

        assertThat(target.targetType()).isEqualTo(ReportService.TARGET_TYPE_POST);
        assertThat(target.targetId()).isEqualTo(88);
        assertThat(target.targetUserId()).isEqualTo(9);
    }

    @Test
    void resolveTargetShouldProjectCommentOwner() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        ModerationTargetResolver resolver = new ModerationTargetResolver(discussPostMapper, commentMapper);
        Comment comment = new Comment();
        comment.setId(66);
        comment.setUserId(7);
        comment.setStatus(0);
        when(commentMapper.selectCommentById(66)).thenReturn(comment);

        ModerationTargetResolver.ResolvedTarget target = resolver.resolveTarget(report(12, ReportService.TARGET_TYPE_COMMENT, 66));

        assertThat(target.targetType()).isEqualTo(ReportService.TARGET_TYPE_COMMENT);
        assertThat(target.targetId()).isEqualTo(66);
        assertThat(target.targetUserId()).isEqualTo(7);
    }

    @Test
    void resolveTargetShouldTreatUserTargetAsSelfOwned() {
        ModerationTargetResolver resolver = new ModerationTargetResolver(mock(DiscussPostMapper.class), mock(CommentMapper.class));

        ModerationTargetResolver.ResolvedTarget target = resolver.resolveTarget(report(12, ReportService.TARGET_TYPE_USER, 99));

        assertThat(target.targetType()).isEqualTo(ReportService.TARGET_TYPE_USER);
        assertThat(target.targetId()).isEqualTo(99);
        assertThat(target.targetUserId()).isEqualTo(99);
    }

    private Report report(int reportId, int targetType, int targetId) {
        Report report = new Report();
        report.setId(reportId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        return report;
    }
}
