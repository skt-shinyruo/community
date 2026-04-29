package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.port.PostContentPort;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceTest {

    @Test
    void listRecentCommentsByUserShouldDelegateWithSafePagination() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostContentPort postContentPort = mock(PostContentPort.class);
        CommentService service = new CommentService(commentMapper, postContentPort);
        UUID userId = uuid(7);
        Comment comment = new Comment();
        comment.setId(uuid(11));
        when(commentMapper.selectRecentCommentsByUser(userId, 5, 5)).thenReturn(List.of(comment));

        List<Comment> rows = service.listRecentCommentsByUser(userId, 1, 5);

        assertThat(rows).containsExactly(comment);
        verify(commentMapper).selectRecentCommentsByUser(userId, 5, 5);
    }

    @Test
    void listByPostShouldRejectDeletedPostBeforeLoadingComments() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostContentPort postContentPort = mock(PostContentPort.class);
        CommentService service = new CommentService(commentMapper, postContentPort);
        UUID postId = uuid(101);
        when(postContentPort.getById(postId)).thenThrow(new BusinessException(POST_NOT_FOUND));

        assertThatThrownBy(() -> service.listByPost(postId, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(POST_NOT_FOUND));

        verify(commentMapper, never()).selectCommentsByEntity(eq(CommentService.ENTITY_TYPE_POST), eq(postId), anyInt(), anyInt());
    }
}
