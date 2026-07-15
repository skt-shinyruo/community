package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
import static com.nowcoder.community.content.support.CommentTestBuilder.aComment;
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
        PostContentRepository postContentPort = mock(PostContentRepository.class);
        MyBatisCommentContentRepository service = new MyBatisCommentContentRepository(commentMapper, postContentPort);
        UUID userId = uuid(7);
        CommentDataObject row = aComment().id(uuid(11)).userId(userId).buildDataObject();
        when(commentMapper.selectRecentCommentsByUser(userId, 5, 5)).thenReturn(List.of(row));

        List<Comment> rows = service.listRecentCommentsByUser(userId, 1, 5);

        assertThat(rows).extracting(Comment::getId).containsExactly(uuid(11));
        verify(commentMapper).selectRecentCommentsByUser(userId, 5, 5);
    }

    @Test
    void listRootCommentsShouldRejectDeletedPostBeforeLoadingComments() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostContentRepository postContentPort = mock(PostContentRepository.class);
        MyBatisCommentContentRepository service = new MyBatisCommentContentRepository(commentMapper, postContentPort);
        UUID postId = uuid(101);
        when(postContentPort.getById(postId)).thenThrow(new BusinessException(POST_NOT_FOUND));

        assertThatThrownBy(() -> service.listRootComments(postId, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(POST_NOT_FOUND));

        verify(commentMapper, never()).selectRootComments(eq(postId), anyInt(), anyInt());
    }

    @Test
    void listRootCommentsWithFetchLimitShouldUsePageSizeForOffset() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostContentRepository postContentPort = mock(PostContentRepository.class);
        MyBatisCommentContentRepository service = new MyBatisCommentContentRepository(commentMapper, postContentPort);
        UUID postId = uuid(101);
        CommentDataObject row = aComment().id(uuid(11)).postId(postId).buildDataObject();
        when(commentMapper.selectRootComments(postId, 20, 11)).thenReturn(List.of(row));

        List<Comment> rows = service.listRootComments(postId, 2, 10, 11);

        assertThat(rows).extracting(Comment::getId).containsExactly(uuid(11));
        verify(commentMapper).selectRootComments(postId, 20, 11);
    }

    @Test
    void listRepliesWithFetchLimitShouldUsePageSizeForOffset() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostContentRepository postContentPort = mock(PostContentRepository.class);
        MyBatisCommentContentRepository service = new MyBatisCommentContentRepository(commentMapper, postContentPort);
        UUID rootCommentId = uuid(201);
        CommentDataObject row = aComment()
                .id(uuid(11))
                .rootCommentId(rootCommentId)
                .parentCommentId(rootCommentId)
                .buildDataObject();
        when(commentMapper.selectRepliesByRootComment(rootCommentId, 20, 11)).thenReturn(List.of(row));

        List<Comment> rows = service.listReplies(rootCommentId, 2, 10, 11);

        assertThat(rows).extracting(Comment::getId).containsExactly(uuid(11));
        verify(commentMapper).selectRepliesByRootComment(rootCommentId, 20, 11);
    }
}
