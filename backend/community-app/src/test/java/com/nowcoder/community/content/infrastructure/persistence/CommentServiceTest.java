package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
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
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void listRootCommentsAfterShouldPassBoundaryAndClampFetchLimit() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostContentRepository postContentPort = mock(PostContentRepository.class);
        MyBatisCommentContentRepository service = new MyBatisCommentContentRepository(commentMapper, postContentPort);
        UUID postId = uuid(301);
        Date boundaryTime = new Date(1_234L);
        UUID boundaryId = uuid(302);
        CommentDataObject row = aComment().id(uuid(303)).postId(postId).buildDataObject();
        when(commentMapper.selectRootCommentsAfter(postId, boundaryTime, boundaryId, 51))
                .thenReturn(List.of(row));

        List<Comment> rows = service.listRootCommentsAfter(postId, boundaryTime, boundaryId, 999);

        assertThat(rows).extracting(Comment::getId).containsExactly(uuid(303));
        verify(postContentPort).getById(postId);
        verify(commentMapper).selectRootCommentsAfter(postId, boundaryTime, boundaryId, 51);
    }

    @Test
    void listRepliesAfterShouldAllowEmptyBoundaryAndClampFetchLimit() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostContentRepository postContentPort = mock(PostContentRepository.class);
        MyBatisCommentContentRepository service = new MyBatisCommentContentRepository(commentMapper, postContentPort);
        UUID rootCommentId = uuid(401);
        CommentDataObject row = aComment()
                .id(uuid(402))
                .rootCommentId(rootCommentId)
                .parentCommentId(rootCommentId)
                .buildDataObject();
        when(commentMapper.selectRepliesAfter(rootCommentId, null, null, 1)).thenReturn(List.of(row));

        List<Comment> rows = service.listRepliesAfter(rootCommentId, null, null, 0);

        assertThat(rows).extracting(Comment::getId).containsExactly(uuid(402));
        verify(commentMapper).selectRepliesAfter(rootCommentId, null, null, 1);
    }

    @Test
    void keysetBoundaryTimeAndIdShouldBeBothPresentOrBothAbsent() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostContentRepository postContentPort = mock(PostContentRepository.class);
        MyBatisCommentContentRepository service = new MyBatisCommentContentRepository(commentMapper, postContentPort);
        UUID postId = uuid(501);
        UUID rootCommentId = uuid(502);
        UUID boundaryId = uuid(503);
        Date boundaryTime = new Date(5_000L);

        assertInvalidBoundary(() -> service.listRootCommentsAfter(postId, boundaryTime, null, 10));
        assertInvalidBoundary(() -> service.listRootCommentsAfter(postId, null, boundaryId, 10));
        assertInvalidBoundary(() -> service.listRepliesAfter(rootCommentId, boundaryTime, null, 10));
        assertInvalidBoundary(() -> service.listRepliesAfter(rootCommentId, null, boundaryId, 10));

        verifyNoInteractions(commentMapper);
    }

    private void assertInvalidBoundary(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(INVALID_ARGUMENT));
    }
}
