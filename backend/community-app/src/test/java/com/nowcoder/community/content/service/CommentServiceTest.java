package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.mapper.CommentMapper;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.action.UserPointsAwardActionApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceTest {

    @Test
    void listRecentCommentsByUserShouldDelegateWithSafePagination() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        SocialBlockQueryApi blockQueryApplicationService = mock(SocialBlockQueryApi.class);
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

        Comment comment = new Comment();
        UUID userId = uuid(7);
        comment.setId(uuid(11));
        when(commentMapper.selectRecentCommentsByUser(userId, 5, 5)).thenReturn(List.of(comment));

        List<Comment> rows = service.listRecentCommentsByUser(userId, 1, 5);

        assertThat(rows).hasSize(1);
        verify(commentMapper).selectRecentCommentsByUser(userId, 5, 5);
    }

    @Test
    void listByPostShouldRejectDeletedPost() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        SocialBlockQueryApi blockQueryApplicationService = mock(SocialBlockQueryApi.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());
        UUID postId = uuid(101);

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

        when(postService.getById(postId)).thenThrow(new BusinessException(POST_NOT_FOUND));

        assertThatThrownBy(() -> service.listByPost(postId, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(POST_NOT_FOUND));

        verify(commentMapper, never()).selectCommentsByEntity(eq(CommentService.ENTITY_TYPE_POST), eq(postId), anyInt(), anyInt());
    }

    @Test
    void listByPostShouldRejectMissingPost() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        SocialBlockQueryApi blockQueryApplicationService = mock(SocialBlockQueryApi.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());
        UUID postId = uuid(102);

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

        when(postService.getById(postId)).thenThrow(new BusinessException(POST_NOT_FOUND));

        assertThatThrownBy(() -> service.listByPost(postId, 1, 5))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(POST_NOT_FOUND));

        verify(commentMapper, never()).selectCommentsByEntity(eq(CommentService.ENTITY_TYPE_POST), eq(postId), anyInt(), anyInt());
    }

    @Test
    void addReplyShouldFailWhenTargetCommentNotBelongToPost() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        SocialBlockQueryApi blockQueryApplicationService = mock(SocialBlockQueryApi.class);
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

        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID targetCommentId = uuid(200);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(postAuthorId);
        when(postService.getById(postId)).thenReturn(post);

        Comment target = new Comment();
        target.setId(targetCommentId);
        target.setUserId(postAuthorId);
        target.setStatus(0);
        target.setEntityType(CommentService.ENTITY_TYPE_POST);
        target.setEntityId(uuid(999));
        when(commentMapper.selectCommentById(targetCommentId)).thenReturn(target);

        assertThatThrownBy(() -> service.addComment(uuid(1), postId, CommentService.ENTITY_TYPE_COMMENT, targetCommentId, null, "hi"))
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
        SocialBlockQueryApi blockQueryApplicationService = mock(SocialBlockQueryApi.class);
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

        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID targetCommentId = uuid(200);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(postAuthorId);
        when(postService.getById(postId)).thenReturn(post);

        Comment target = new Comment();
        target.setId(targetCommentId);
        target.setUserId(postAuthorId);
        target.setStatus(0);
        target.setEntityType(CommentService.ENTITY_TYPE_COMMENT); // reply
        target.setEntityId(uuid(123));
        when(commentMapper.selectCommentById(targetCommentId)).thenReturn(target);

        assertThatThrownBy(() -> service.addComment(uuid(1), postId, CommentService.ENTITY_TYPE_COMMENT, targetCommentId, null, "hi"))
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
        SocialBlockQueryApi blockQueryApplicationService = mock(SocialBlockQueryApi.class);
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

        UUID actorUserId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(postAuthorId);
        when(postService.getById(postId)).thenReturn(post);

        when(sensitiveFilter.filter(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(postScoreQueue).add(any(UUID.class));
        doNothing().when(eventPublisher).publishCommentCreated(any());

        doNothing().when(moderationGuard).assertCanSpeak(eq(actorUserId));
        when(blockQueryApplicationService.isEitherBlocked(actorUserId, postAuthorId)).thenThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "user 模块不可用"));

        assertThatThrownBy(() -> service.addComment(actorUserId, postId, CommentService.ENTITY_TYPE_POST, null, null, "hi"))
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
        SocialBlockQueryApi blockQueryApplicationService = mock(SocialBlockQueryApi.class);
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

        UUID actorUserId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(postAuthorId);
        when(postService.getById(postId)).thenReturn(post);

        doNothing().when(moderationGuard).assertCanSpeak(eq(actorUserId));
        when(blockQueryApplicationService.isEitherBlocked(actorUserId, postAuthorId)).thenReturn(true);

        assertThatThrownBy(() -> service.addComment(actorUserId, postId, CommentService.ENTITY_TYPE_POST, null, null, "hi"))
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
        SocialBlockQueryApi blockQueryApplicationService = mock(SocialBlockQueryApi.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        UserPointsAwardActionApi pointsAwardService = mock(UserPointsAwardActionApi.class);
        GrowthTaskProgressActionApi taskProgressTriggerService = mock(GrowthTaskProgressActionApi.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        CommentService service = new CommentService(
                commentMapper,
                postService,
                sensitiveFilter,
                postScoreQueue,
                eventPublisher,
                blockQueryApplicationService,
                moderationGuard,
                textCodec,
                pointsAwardService,
                taskProgressTriggerService
        );

        UUID actorUserId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(postAuthorId);
        when(postService.getById(postId)).thenReturn(post);

        when(sensitiveFilter.filter(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(postScoreQueue).add(any(UUID.class));
        doNothing().when(eventPublisher).publishCommentCreated(any());

        doNothing().when(moderationGuard).assertCanSpeak(eq(actorUserId));
        when(blockQueryApplicationService.isEitherBlocked(actorUserId, postAuthorId)).thenReturn(false);

        doAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            c.setId(uuid(123));
            return 1;
        }).when(commentMapper).insertComment(any(Comment.class));

        service.addComment(actorUserId, postId, CommentService.ENTITY_TYPE_POST, null, null, "hi");

        verify(commentMapper).insertComment(any(Comment.class));
        var inOrder = inOrder(pointsAwardService, taskProgressTriggerService, eventPublisher);
        inOrder.verify(pointsAwardService).awardCommentCreated(any());
        inOrder.verify(taskProgressTriggerService).triggerCommentCreated(any());
        inOrder.verify(eventPublisher).publishCommentCreated(any());
    }

    @Test
    void updateCommentShouldEscapeFilterAndPersist() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostService postService = mock(PostService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        PostScoreQueue postScoreQueue = mock(PostScoreQueue.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        SocialBlockQueryApi blockQueryApplicationService = mock(SocialBlockQueryApi.class);
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

        UUID actorUserId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        when(postService.getById(postId)).thenReturn(post);

        Comment existed = new Comment();
        existed.setId(commentId);
        existed.setUserId(actorUserId);
        existed.setStatus(0);
        existed.setEntityType(CommentService.ENTITY_TYPE_POST);
        existed.setEntityId(postId);
        existed.setCreateTime(new java.util.Date());
        when(commentMapper.selectCommentById(commentId)).thenReturn(existed);

        doNothing().when(moderationGuard).assertCanSpeak(eq(actorUserId));
        when(sensitiveFilter.filter("hello &amp; world")).thenReturn("clean");
        when(commentMapper.updateCommentContent(eq(commentId), eq("clean"), any())).thenReturn(1);

        service.updateComment(actorUserId, postId, commentId, "hello & world");

        verify(commentMapper).updateCommentContent(eq(commentId), eq("clean"), any());
    }
}
