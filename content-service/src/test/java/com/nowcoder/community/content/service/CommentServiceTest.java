package com.nowcoder.community.content.service;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.dao.CommentMapper;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.projection.UserModerationProjectionRepository;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.content.util.SensitiveFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceTest {

    @Test
    void addCommentShouldNotCallDownstreamWhenProjectionExists() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        UserModerationProjectionRepository projectionRepository = mock(UserModerationProjectionRepository.class);
        UserModerationClient userModerationClient = mock(UserModerationClient.class);

        // 投影存在：assertCanSpeak 直接通过，不应触发下游 user-service 访问
        doNothing().when(projectionRepository).assertCanSpeak(anyInt());
        UserModerationGuard moderationGuard = new UserModerationGuard(projectionRepository, userModerationClient);

        CommentService service = new CommentService(
                commentMapper,
                postService,
                sensitiveFilter,
                postScoreQueue,
                eventPublisher,
                projectionRepository,
                moderationGuard
        );

        DiscussPost post = new DiscussPost();
        post.setId(100);
        post.setUserId(2);
        when(postService.getById(100)).thenReturn(post);

        doNothing().when(projectionRepository).assertNotBlocked(anyInt(), anyInt());
        when(sensitiveFilter.filter(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(postScoreQueue).add(anyInt());
        doNothing().when(eventPublisher).publishCommentCreated(any());

        doAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            c.setId(123);
            return 1;
        }).when(commentMapper).insertComment(any(Comment.class));

        service.addComment(1, 100, CommentService.ENTITY_TYPE_POST, null, 0, "hi");

        verify(userModerationClient, never()).getStatus(anyInt());
    }

    @Test
    void addCommentShouldFailClosedWhenProjectionMissingAndDownstreamUnavailable() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        UserModerationProjectionRepository projectionRepository = mock(UserModerationProjectionRepository.class);
        UserModerationClient userModerationClient = mock(UserModerationClient.class);

        // 投影缺失：触发 bootstrap 回填；但下游不可用时必须 fail-closed 返回 503
        doThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "处罚状态投影缺失"))
                .when(projectionRepository)
                .assertCanSpeak(anyInt());
        doThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "user-service 不可用"))
                .when(userModerationClient)
                .getStatus(anyInt());

        UserModerationGuard moderationGuard = new UserModerationGuard(projectionRepository, userModerationClient);

        CommentService service = new CommentService(
                commentMapper,
                postService,
                sensitiveFilter,
                postScoreQueue,
                eventPublisher,
                projectionRepository,
                moderationGuard
        );

        DiscussPost post = new DiscussPost();
        post.setId(100);
        post.setUserId(2);
        when(postService.getById(100)).thenReturn(post);

        assertThatThrownBy(() -> service.addComment(1, 100, CommentService.ENTITY_TYPE_POST, null, 0, "hi"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
                });

        verify(commentMapper, never()).insertComment(any(Comment.class));
    }
}
