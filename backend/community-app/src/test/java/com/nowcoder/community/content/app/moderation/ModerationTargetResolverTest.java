package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.content.mapper.CommentMapper;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import com.nowcoder.community.content.service.ReportService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModerationTargetResolverTest {

    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-7000-8000-000000000301");

    @Test
    void resolveTargetShouldProjectPostOwner() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        ModerationTargetResolver resolver = new ModerationTargetResolver(discussPostMapper, commentMapper);
        UUID postId = uuid(88);
        UUID postOwnerId = uuid(9);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(postOwnerId);
        post.setStatus(0);
        when(discussPostMapper.selectDiscussPostById(postId)).thenReturn(post);

        ModerationTargetResolver.ResolvedTarget target = resolver.resolveTarget(report(REPORT_ID, ReportService.TARGET_TYPE_POST, postId));

        assertThat(target.targetType()).isEqualTo(ReportService.TARGET_TYPE_POST);
        assertThat(target.targetId()).isEqualTo(postId);
        assertThat(target.targetUserId()).isEqualTo(postOwnerId);
    }

    @Test
    void resolveTargetShouldProjectCommentOwner() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        ModerationTargetResolver resolver = new ModerationTargetResolver(discussPostMapper, commentMapper);
        UUID commentId = uuid(66);
        UUID commentOwnerId = uuid(7);
        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setUserId(commentOwnerId);
        comment.setStatus(0);
        when(commentMapper.selectCommentById(commentId)).thenReturn(comment);

        ModerationTargetResolver.ResolvedTarget target = resolver.resolveTarget(report(REPORT_ID, ReportService.TARGET_TYPE_COMMENT, commentId));

        assertThat(target.targetType()).isEqualTo(ReportService.TARGET_TYPE_COMMENT);
        assertThat(target.targetId()).isEqualTo(commentId);
        assertThat(target.targetUserId()).isEqualTo(commentOwnerId);
    }

    @Test
    void resolveTargetShouldTreatUserTargetAsSelfOwned() {
        ModerationTargetResolver resolver = new ModerationTargetResolver(mock(DiscussPostMapper.class), mock(CommentMapper.class));
        UUID userId = uuid(99);

        ModerationTargetResolver.ResolvedTarget target = resolver.resolveTarget(report(REPORT_ID, ReportService.TARGET_TYPE_USER, userId));

        assertThat(target.targetType()).isEqualTo(ReportService.TARGET_TYPE_USER);
        assertThat(target.targetId()).isEqualTo(userId);
        assertThat(target.targetUserId()).isEqualTo(userId);
    }

    private Report report(UUID reportId, int targetType, UUID targetId) {
        Report report = new Report();
        report.setId(reportId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        return report;
    }
}
