package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.dto.CreatePostRequest;
import com.nowcoder.community.content.dto.CreatePostResponse;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import com.nowcoder.community.content.dto.UserRecentCommentResponse;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.service.BookmarkService;
import com.nowcoder.community.content.service.CommentService;
import com.nowcoder.community.content.service.PostCommandService;
import com.nowcoder.community.content.service.PostService;
import com.nowcoder.community.content.service.SubscriptionService;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;

@ExtendWith(OutputCaptureExtension.class)
class PostFacadeServiceTest {

    @Test
    void listShouldAssemblePostSummariesOutsideController() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        PostCommandService postCommandService = mock(PostCommandService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        DiscussPost post = new DiscussPost();
        post.setId(10);
        post.setUserId(2);
        post.setTitle("&lt;title&gt;");
        post.setContent("&lt;content&gt;");
        post.setCommentCount(3);

        Comment lastActivity = new Comment();
        lastActivity.setUserId(3);
        lastActivity.setCreateTime(new java.util.Date());
        lastActivity.setContent("&lt;latest reply&gt;");

        when(postService.listPosts(0, 10, PostService.ORDER_LATEST, null, null)).thenReturn(List.of(post));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(10))).thenReturn(Map.of(10, lastActivity));
        when(tagService.getTagsByPostIds(List.of(10))).thenReturn(Map.of(10, List.of("java")));

        PostFacadeService service = new PostFacadeService(
                postService,
                commentService,
                sensitiveFilter,
                likeQueryService,
                postCommandService,
                tagService,
                bookmarkService,
                subscriptionService,
                idempotencyGuard,
                textCodec
        );

        List<PostSummaryResponse> items = service.list(null, "latest", null, null, false, 0, 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getId()).isEqualTo(10);
        assertThat(items.get(0).getTitle()).isEqualTo("<title>");
        assertThat(items.get(0).getTags()).containsExactly("java");
        assertThat(items.get(0).getLastActivityTime()).isEqualTo(lastActivity.getCreateTime());
        assertThat(items.get(0).getLastReplyPreview()).isEqualTo("<latest reply>");
    }

    @Test
    void createShouldEscapeFilterAndDelegateCommand() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        PostCommandService postCommandService = mock(PostCommandService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        when(sensitiveFilter.filter("<title>")).thenReturn("title");
        when(sensitiveFilter.filter("<content>")).thenReturn("content");
        when(postCommandService.createPost(eq(7), eq("title"), eq("content"), eq(1), eq(List.of("java")))).thenReturn(99);
        when(idempotencyGuard.executeRequired(eq("content:create_post"), eq(7), eq("idem-1"), eq(CreatePostResponse.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<CreatePostResponse>>getArgument(4).get());

        PostFacadeService service = new PostFacadeService(
                postService,
                commentService,
                sensitiveFilter,
                likeQueryService,
                postCommandService,
                tagService,
                bookmarkService,
                subscriptionService,
                idempotencyGuard,
                textCodec
        );

        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("<title>");
        request.setContent("<content>");
        request.setCategoryId(1);
        request.setTags(List.of("java"));

        CreatePostResponse response = service.create(7, "idem-1", request);

        assertThat(response.getPostId()).isEqualTo(99);
    }

    @Test
    void listPostsByUserShouldAssembleRecentSummaries() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        PostCommandService postCommandService = mock(PostCommandService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        DiscussPost first = new DiscussPost();
        first.setId(21);
        first.setUserId(7);
        first.setTitle("&lt;first&gt;");
        first.setCommentCount(5);

        DiscussPost second = new DiscussPost();
        second.setId(22);
        second.setUserId(7);
        second.setTitle("&lt;second&gt;");
        second.setCommentCount(1);

        Comment lastActivity = new Comment();
        lastActivity.setUserId(11);
        lastActivity.setCreateTime(new java.util.Date());
        lastActivity.setContent("&lt;newest&gt;");

        when(postService.listPostsByUser(7, 0, 3)).thenReturn(List.of(first, second));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(21, 22))).thenReturn(Map.of(21, lastActivity));
        when(tagService.getTagsByPostIds(List.of(21, 22))).thenReturn(Map.of(21, List.of("java"), 22, List.of("spring")));

        PostFacadeService service = new PostFacadeService(
                postService,
                commentService,
                sensitiveFilter,
                likeQueryService,
                postCommandService,
                tagService,
                bookmarkService,
                subscriptionService,
                idempotencyGuard,
                textCodec
        );

        List<PostSummaryResponse> items = service.listPostsByUser(7, 0, 3);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getUserId()).isEqualTo(7);
        assertThat(items.get(0).getTitle()).isEqualTo("<first>");
        assertThat(items.get(0).getTags()).containsExactly("java");
        assertThat(items.get(0).getLastReplyUserId()).isEqualTo(11);
        assertThat(items.get(0).getLastReplyPreview()).isEqualTo("<newest>");
        assertThat(items.get(1).getTags()).containsExactly("spring");
    }

    @Test
    void listRecentCommentsByUserShouldResolveDirectCommentsAndReplies() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        PostCommandService postCommandService = mock(PostCommandService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        Comment direct = new Comment();
        direct.setId(31);
        direct.setUserId(7);
        direct.setEntityType(CommentService.ENTITY_TYPE_POST);
        direct.setEntityId(201);
        direct.setContent("&lt;direct&gt;");
        direct.setCreateTime(new java.util.Date(2000));

        Comment reply = new Comment();
        reply.setId(32);
        reply.setUserId(7);
        reply.setEntityType(CommentService.ENTITY_TYPE_COMMENT);
        reply.setEntityId(88);
        reply.setContent("&lt;reply&gt;");
        reply.setCreateTime(new java.util.Date(3000));

        Comment parent = new Comment();
        parent.setId(88);
        parent.setEntityType(CommentService.ENTITY_TYPE_POST);
        parent.setEntityId(202);

        DiscussPost firstPost = new DiscussPost();
        firstPost.setId(201);
        firstPost.setTitle("&lt;first&gt;");

        DiscussPost secondPost = new DiscussPost();
        secondPost.setId(202);
        secondPost.setTitle("&lt;second&gt;");

        when(commentService.listRecentCommentsByUser(7, 0, 3)).thenReturn(List.of(reply, direct));
        when(commentService.getById(88)).thenReturn(parent);
        when(postService.getById(201)).thenReturn(firstPost);
        when(postService.getById(202)).thenReturn(secondPost);

        PostFacadeService service = new PostFacadeService(
                postService,
                commentService,
                sensitiveFilter,
                likeQueryService,
                postCommandService,
                tagService,
                bookmarkService,
                subscriptionService,
                idempotencyGuard,
                textCodec
        );

        List<UserRecentCommentResponse> items = service.listRecentCommentsByUser(7, 0, 3);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getPostId()).isEqualTo(202);
        assertThat(items.get(0).getPostTitle()).isEqualTo("<second>");
        assertThat(items.get(0).getContent()).isEqualTo("<reply>");
        assertThat(items.get(1).getPostId()).isEqualTo(201);
        assertThat(items.get(1).getPostTitle()).isEqualTo("<first>");
    }

    @Test
    void listRecentCommentsByUserShouldSkipBrokenReplyTargetsInsteadOfFailingWholeFeed() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        PostCommandService postCommandService = mock(PostCommandService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        Comment brokenReply = new Comment();
        brokenReply.setId(41);
        brokenReply.setUserId(7);
        brokenReply.setEntityType(CommentService.ENTITY_TYPE_COMMENT);
        brokenReply.setEntityId(404);
        brokenReply.setContent("&lt;reply&gt;");
        brokenReply.setCreateTime(new java.util.Date(3000));

        Comment direct = new Comment();
        direct.setId(42);
        direct.setUserId(7);
        direct.setEntityType(CommentService.ENTITY_TYPE_POST);
        direct.setEntityId(201);
        direct.setContent("&lt;direct&gt;");
        direct.setCreateTime(new java.util.Date(2000));

        DiscussPost firstPost = new DiscussPost();
        firstPost.setId(201);
        firstPost.setTitle("&lt;first&gt;");

        when(commentService.listRecentCommentsByUser(7, 0, 3)).thenReturn(List.of(brokenReply, direct));
        when(commentService.getById(404)).thenThrow(new BusinessException(COMMENT_NOT_FOUND));
        when(postService.getById(201)).thenReturn(firstPost);

        PostFacadeService service = new PostFacadeService(
                postService,
                commentService,
                sensitiveFilter,
                likeQueryService,
                postCommandService,
                tagService,
                bookmarkService,
                subscriptionService,
                idempotencyGuard,
                textCodec
        );

        List<UserRecentCommentResponse> items = service.listRecentCommentsByUser(7, 0, 3);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getId()).isEqualTo(42);
        assertThat(items.get(0).getPostId()).isEqualTo(201);
    }

    @Test
    void listPostsByIdsShouldPreserveRequestedOrder() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        PostCommandService postCommandService = mock(PostCommandService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        DiscussPost first = new DiscussPost();
        first.setId(12);
        first.setUserId(2);
        first.setTitle("&lt;first&gt;");
        first.setCommentCount(4);

        DiscussPost second = new DiscussPost();
        second.setId(9);
        second.setUserId(3);
        second.setTitle("&lt;second&gt;");
        second.setCommentCount(1);

        Comment lastActivity = new Comment();
        lastActivity.setUserId(5);
        lastActivity.setCreateTime(new java.util.Date());

        when(postService.listPostsByIds(List.of(12, 9))).thenReturn(List.of(first, second));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(12, 9))).thenReturn(Map.of(12, lastActivity));
        when(tagService.getTagsByPostIds(List.of(12, 9))).thenReturn(Map.of(12, List.of("java"), 9, List.of("spring")));

        PostFacadeService service = new PostFacadeService(
                postService,
                commentService,
                sensitiveFilter,
                likeQueryService,
                postCommandService,
                tagService,
                bookmarkService,
                subscriptionService,
                idempotencyGuard,
                textCodec
        );

        List<PostSummaryResponse> items = service.listPostsByIds(List.of(12, 9));

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getId()).isEqualTo(12);
        assertThat(items.get(1).getId()).isEqualTo(9);
        assertThat(items.get(0).getCommentCount()).isEqualTo(4);
    }

    @Test
    void moderationCommandsShouldDelegateWithoutLegacyFacadeAuditLogs(CapturedOutput output) {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        PostCommandService postCommandService = mock(PostCommandService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        PostFacadeService service = new PostFacadeService(
                postService,
                commentService,
                sensitiveFilter,
                likeQueryService,
                postCommandService,
                tagService,
                bookmarkService,
                subscriptionService,
                idempotencyGuard,
                textCodec
        );

        service.deleteByAuthor(7, 101);
        service.top(9, 101);
        service.wonderful(9, 101);
        service.delete(99, 101);

        verify(postCommandService).deletePostByAuthor(7, 101);
        verify(postCommandService).topPost(9, 101);
        verify(postCommandService).markWonderful(9, 101);
        verify(postCommandService).adminDelete(99, 101);
        assertThat(output.getAll()).doesNotContain("[audit] action=post_");
    }
}
