package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisCommentRepositoryTest {

    @Test
    void createShouldMapDraftIntoCommentSetGeneratedUuidStatusAndReturnGeneratedId() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        UuidV7Generator idGenerator = new UuidV7Generator(Clock.fixed(
                Instant.parse("2026-04-29T01:02:03Z"),
                ZoneOffset.UTC
        ));
        MyBatisCommentRepository repository = new MyBatisCommentRepository(commentMapper, idGenerator);
        UUID userId = uuid(101);
        UUID entityId = uuid(102);
        UUID targetId = uuid(103);
        Date createTime = Date.from(Instant.parse("2026-04-29T01:02:04Z"));
        CommentDraft draft = new CommentDraft(userId, 1, entityId, targetId, "hello", createTime);

        UUID commentId = repository.create(draft);

        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insertComment(commentCaptor.capture());
        Comment inserted = commentCaptor.getValue();
        assertThat(commentId).isEqualTo(inserted.getId());
        assertThat(inserted.getId()).isNotNull();
        assertThat(inserted.getId().version()).isEqualTo(7);
        assertThat(inserted.getUserId()).isEqualTo(userId);
        assertThat(inserted.getEntityType()).isEqualTo(1);
        assertThat(inserted.getEntityId()).isEqualTo(entityId);
        assertThat(inserted.getTargetId()).isEqualTo(targetId);
        assertThat(inserted.getContent()).isEqualTo("hello");
        assertThat(inserted.getStatus()).isZero();
        assertThat(inserted.getCreateTime()).isEqualTo(createTime);
    }

    @Test
    void getRequiredSnapshotShouldThrowCommentNotFoundWhenMapperReturnsInactiveComment() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        MyBatisCommentRepository repository = new MyBatisCommentRepository(
                commentMapper,
                new UuidV7Generator(Clock.fixed(Instant.parse("2026-04-29T01:02:03Z"), ZoneOffset.UTC))
        );
        UUID commentId = uuid(201);
        Comment inactive = comment(commentId, 2);
        when(commentMapper.selectCommentById(commentId)).thenReturn(inactive);

        assertThatThrownBy(() -> repository.getRequiredSnapshot(commentId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(COMMENT_NOT_FOUND);
    }

    @Test
    void updateContentShouldThrowInvalidArgumentWhenNoRowsUpdated() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        MyBatisCommentRepository repository = new MyBatisCommentRepository(
                commentMapper,
                new UuidV7Generator(Clock.fixed(Instant.parse("2026-04-29T01:02:03Z"), ZoneOffset.UTC))
        );
        UUID commentId = uuid(301);
        Date updateTime = Date.from(Instant.parse("2026-04-29T01:02:05Z"));
        when(commentMapper.updateCommentContent(commentId, "updated", updateTime)).thenReturn(0);

        assertThatThrownBy(() -> repository.updateContent(commentId, "updated", updateTime))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(INVALID_ARGUMENT))
                .hasMessage("更新评论失败");
    }

    private static Comment comment(UUID commentId, int status) {
        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setUserId(uuid(401));
        comment.setEntityType(1);
        comment.setEntityId(uuid(402));
        comment.setTargetId(uuid(403));
        comment.setContent("comment");
        comment.setStatus(status);
        comment.setCreateTime(Date.from(Instant.parse("2026-04-29T01:02:06Z")));
        comment.setUpdateTime(Date.from(Instant.parse("2026-04-29T01:02:07Z")));
        comment.setEditCount(1);
        return comment;
    }
}
