package com.nowcoder.community.content.service;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.dao.CommentMapper;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.social.application.BlockQueryApplicationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceTest {

    @Test
    void addReplyShouldFailWhenTargetCommentNotBelongToPost() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        BlockQueryApplicationService blockQueryApplicationService = mock(BlockQueryApplicationService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        CommentService service = new CommentService(
                commentMapper,
                postService,
                sensitiveFilter,
                postScoreQueue,
                eventPublisher,
                blockQueryApplicationService,
                moderationGuard,
                textCodec
        );

        DiscussPost post = new DiscussPost();
        post.setId(100);
        post.setUserId(2);
        when(postService.getById(100)).thenReturn(post);

        Comment target = new Comment();
        target.setId(200);
        target.setUserId(2);
        target.setStatus(0);
        target.setEntityType(CommentService.ENTITY_TYPE_POST);
        target.setEntityId(999); // 属于其他 post
        when(commentMapper.selectCommentById(200)).thenReturn(target);

        assertThatThrownBy(() -> service.addComment(1, 100, CommentService.ENTITY_TYPE_COMMENT, 200, null, "hi"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    org.assertj.core.api.Assertions.assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
                });

        verify(commentMapper, never()).insertComment(any(Comment.class));
        verify(eventPublisher, never()).publishCommentCreated(any());
    }

    @Test
    void addReplyShouldFailWhenTargetCommentIsReplyItself() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        BlockQueryApplicationService blockQueryApplicationService = mock(BlockQueryApplicationService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        CommentService service = new CommentService(
                commentMapper,
                postService,
                sensitiveFilter,
                postScoreQueue,
                eventPublisher,
                blockQueryApplicationService,
                moderationGuard,
                textCodec
        );

        DiscussPost post = new DiscussPost();
        post.setId(100);
        post.setUserId(2);
        when(postService.getById(100)).thenReturn(post);

        Comment target = new Comment();
        target.setId(200);
        target.setUserId(2);
        target.setStatus(0);
        target.setEntityType(CommentService.ENTITY_TYPE_COMMENT); // reply
        target.setEntityId(123);
        when(commentMapper.selectCommentById(200)).thenReturn(target);

        assertThatThrownBy(() -> service.addComment(1, 100, CommentService.ENTITY_TYPE_COMMENT, 200, null, "hi"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    org.assertj.core.api.Assertions.assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
                });

        verify(commentMapper, never()).insertComment(any(Comment.class));
        verify(eventPublisher, never()).publishCommentCreated(any());
    }

    @Test
    void addCommentShouldFailClosedWhenUserServiceUnavailable() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        BlockQueryApplicationService blockQueryApplicationService = mock(BlockQueryApplicationService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        CommentService service = new CommentService(
                commentMapper,
                postService,
                sensitiveFilter,
                postScoreQueue,
                eventPublisher,
                blockQueryApplicationService,
                moderationGuard,
                textCodec
        );

        DiscussPost post = new DiscussPost();
        post.setId(100);
        post.setUserId(2);
        when(postService.getById(100)).thenReturn(post);

        when(sensitiveFilter.filter(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(postScoreQueue).add(anyInt());
        doNothing().when(eventPublisher).publishCommentCreated(any());

        doNothing().when(moderationGuard).assertCanSpeak(eq(1));
        when(blockQueryApplicationService.isEitherBlocked(1, 2)).thenThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "user-service 不可用"));

        assertThatThrownBy(() -> service.addComment(1, 100, CommentService.ENTITY_TYPE_POST, null, 0, "hi"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    org.assertj.core.api.Assertions.assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
                });

        verify(commentMapper, never()).insertComment(any(Comment.class));
        verify(eventPublisher, never()).publishCommentCreated(any());
    }

    @Test
    void addCommentShouldFailWhenEitherBlocked() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        BlockQueryApplicationService blockQueryApplicationService = mock(BlockQueryApplicationService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        CommentService service = new CommentService(
                commentMapper,
                postService,
                sensitiveFilter,
                postScoreQueue,
                eventPublisher,
                blockQueryApplicationService,
                moderationGuard,
                textCodec
        );

        DiscussPost post = new DiscussPost();
        post.setId(100);
        post.setUserId(2);
        when(postService.getById(100)).thenReturn(post);

        doNothing().when(moderationGuard).assertCanSpeak(eq(1));
        when(blockQueryApplicationService.isEitherBlocked(1, 2)).thenReturn(true);

        assertThatThrownBy(() -> service.addComment(1, 100, CommentService.ENTITY_TYPE_POST, null, 0, "hi"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    org.assertj.core.api.Assertions.assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN);
                });

        verify(commentMapper, never()).insertComment(any(Comment.class));
        verify(eventPublisher, never()).publishCommentCreated(any());
    }

    @Test
    void addCommentShouldPublishEventWhenOk() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        BlockQueryApplicationService blockQueryApplicationService = mock(BlockQueryApplicationService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        CommentService service = new CommentService(
                commentMapper,
                postService,
                sensitiveFilter,
                postScoreQueue,
                eventPublisher,
                blockQueryApplicationService,
                moderationGuard,
                textCodec
        );

        DiscussPost post = new DiscussPost();
        post.setId(100);
        post.setUserId(2);
        when(postService.getById(100)).thenReturn(post);

        when(sensitiveFilter.filter(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(postScoreQueue).add(anyInt());
        doNothing().when(eventPublisher).publishCommentCreated(any());

        doNothing().when(moderationGuard).assertCanSpeak(eq(1));
        when(blockQueryApplicationService.isEitherBlocked(1, 2)).thenReturn(false);

        doAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            c.setId(123);
            return 1;
        }).when(commentMapper).insertComment(any(Comment.class));

        service.addComment(1, 100, CommentService.ENTITY_TYPE_POST, null, 0, "hi");

        verify(commentMapper).insertComment(any(Comment.class));
        verify(eventPublisher).publishCommentCreated(any());
    }
}
