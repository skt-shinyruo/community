package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.domain.model.ModerationTarget;
import com.nowcoder.community.content.domain.model.ReportSnapshot;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import com.nowcoder.community.content.infrastructure.persistence.mapper.DiscussPostMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisModerationTargetRepositoryTest {

    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-7000-8000-000000000301");

    @Test
    void resolveTargetShouldProjectPostOwner() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        MyBatisModerationTargetRepository repository = new MyBatisModerationTargetRepository(discussPostMapper, commentMapper);
        UUID postId = uuid(88);
        UUID postOwnerId = uuid(9);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(postOwnerId);
        post.setStatus(0);
        when(discussPostMapper.selectDiscussPostById(postId)).thenReturn(post);

        ModerationTarget target = repository.resolveTarget(report(EntityTypes.POST, postId));

        assertThat(target.targetType()).isEqualTo(EntityTypes.POST);
        assertThat(target.targetId()).isEqualTo(postId);
        assertThat(target.targetUserId()).isEqualTo(postOwnerId);
    }

    @Test
    void resolveTargetShouldProjectCommentOwner() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        MyBatisModerationTargetRepository repository = new MyBatisModerationTargetRepository(discussPostMapper, commentMapper);
        UUID commentId = uuid(66);
        UUID commentOwnerId = uuid(7);
        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setUserId(commentOwnerId);
        comment.setStatus(0);
        when(commentMapper.selectCommentById(commentId)).thenReturn(comment);

        ModerationTarget target = repository.resolveTarget(report(EntityTypes.COMMENT, commentId));

        assertThat(target.targetType()).isEqualTo(EntityTypes.COMMENT);
        assertThat(target.targetId()).isEqualTo(commentId);
        assertThat(target.targetUserId()).isEqualTo(commentOwnerId);
    }

    @Test
    void resolveTargetShouldTreatUserTargetAsSelfOwned() {
        MyBatisModerationTargetRepository repository = new MyBatisModerationTargetRepository(mock(DiscussPostMapper.class), mock(CommentMapper.class));
        UUID userId = uuid(99);

        ModerationTarget target = repository.resolveTarget(report(EntityTypes.USER, userId));

        assertThat(target.targetType()).isEqualTo(EntityTypes.USER);
        assertThat(target.targetId()).isEqualTo(userId);
        assertThat(target.targetUserId()).isEqualTo(userId);
    }

    private ReportSnapshot report(int targetType, UUID targetId) {
        return new ReportSnapshot(REPORT_ID, uuid(7), targetType, targetId, "spam", "detail", 0, new Date());
    }
}
