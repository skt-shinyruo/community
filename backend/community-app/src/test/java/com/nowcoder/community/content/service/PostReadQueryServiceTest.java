package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.model.PostDetailView;
import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.assembler.PostDetailAssembler;
import com.nowcoder.community.content.assembler.PostSummaryAssembler;
import com.nowcoder.community.content.assembler.RecentUserCommentAssembler;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.text.ContentTextCodec;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostReadQueryServiceTest {

    @Test
    void listPostsShouldAssemblePostSummariesOutsideController() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        DiscussPost post = new DiscussPost();
        post.setId(10);
        post.setUserId(2);
        post.setTitle("&lt;title&gt;");
        post.setContent("&lt;content&gt;");
        post.setCommentCount(3);

        Comment lastActivity = new Comment();
        lastActivity.setUserId(3);
        lastActivity.setCreateTime(new Date(2_000));
        lastActivity.setContent("&lt;latest reply&gt;");

        when(postService.listPosts(0, 10, PostService.ORDER_LATEST, null, null)).thenReturn(List.of(post));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(10))).thenReturn(Map.of(10, lastActivity));
        when(tagService.getTagsByPostIds(List.of(10))).thenReturn(Map.of(10, List.of("java")));

        PostReadQueryService service = new PostReadQueryService(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                new PostSummaryAssembler(textCodec()),
                new PostDetailAssembler(textCodec()),
                new RecentUserCommentAssembler(textCodec())
        );

        List<PostSummaryView> items = service.listPosts(0, "latest", null, null, false, 0, 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).id()).isEqualTo(10);
        assertThat(items.get(0).title()).isEqualTo("<title>");
        assertThat(items.get(0).tags()).containsExactly("java");
        assertThat(items.get(0).lastActivityTime()).isEqualTo(lastActivity.getCreateTime());
        assertThat(items.get(0).lastReplyPreview()).isEqualTo("<latest reply>");
    }

    @Test
    void getPostDetailShouldAssembleTagsLikesAndBookmarkState() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        DiscussPost post = new DiscussPost();
        post.setId(10);
        post.setUserId(8);
        post.setTitle("&lt;title&gt;");
        post.setContent("&lt;body&gt;");
        post.setType(1);
        post.setStatus(0);
        post.setCreateTime(new Date(1_000));
        post.setUpdateTime(new Date(2_000));
        post.setEditCount(2);
        post.setCommentCount(5);
        post.setScore(12.5);
        post.setCategoryId(3);

        when(postService.getById(10)).thenReturn(post);
        when(tagService.getTagsByPostIds(List.of(10))).thenReturn(Map.of(10, List.of("java", "spring")));
        when(likeQueryService.countPostLikes(10)).thenReturn(9L);
        when(likeQueryService.hasLikedPost(7, 10)).thenReturn(true);
        when(bookmarkService.hasBookmarked(7, 10)).thenReturn(true);

        PostReadQueryService service = new PostReadQueryService(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                new PostSummaryAssembler(textCodec()),
                new PostDetailAssembler(textCodec()),
                new RecentUserCommentAssembler(textCodec())
        );

        PostDetailView detail = service.getPostDetail(7, 10);

        assertThat(detail.id()).isEqualTo(10);
        assertThat(detail.title()).isEqualTo("<title>");
        assertThat(detail.content()).isEqualTo("<body>");
        assertThat(detail.tags()).containsExactly("java", "spring");
        assertThat(detail.likeCount()).isEqualTo(9L);
        assertThat(detail.liked()).isTrue();
        assertThat(detail.bookmarked()).isTrue();
    }

    @Test
    void listPostsByUserShouldAssembleRecentSummaries() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

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
        lastActivity.setCreateTime(new Date(3_000));
        lastActivity.setContent("&lt;newest&gt;");

        when(postService.listPostsByUser(7, 0, 3)).thenReturn(List.of(first, second));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(21, 22))).thenReturn(Map.of(21, lastActivity));
        when(tagService.getTagsByPostIds(List.of(21, 22))).thenReturn(Map.of(21, List.of("java"), 22, List.of("spring")));

        PostReadQueryService service = new PostReadQueryService(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                new PostSummaryAssembler(textCodec()),
                new PostDetailAssembler(textCodec()),
                new RecentUserCommentAssembler(textCodec())
        );

        List<PostSummaryView> items = service.listPostsByUser(7, 0, 3);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).userId()).isEqualTo(7);
        assertThat(items.get(0).title()).isEqualTo("<first>");
        assertThat(items.get(0).tags()).containsExactly("java");
        assertThat(items.get(0).lastReplyUserId()).isEqualTo(11);
        assertThat(items.get(0).lastReplyPreview()).isEqualTo("<newest>");
        assertThat(items.get(1).tags()).containsExactly("spring");
    }

    @Test
    void listRecentCommentsByUserShouldResolveDirectCommentsAndReplies() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        Comment direct = new Comment();
        direct.setId(31);
        direct.setUserId(7);
        direct.setEntityType(CommentService.ENTITY_TYPE_POST);
        direct.setEntityId(201);
        direct.setContent("&lt;direct&gt;");
        direct.setCreateTime(new Date(2_000));

        Comment reply = new Comment();
        reply.setId(32);
        reply.setUserId(7);
        reply.setEntityType(CommentService.ENTITY_TYPE_COMMENT);
        reply.setEntityId(88);
        reply.setContent("&lt;reply&gt;");
        reply.setCreateTime(new Date(3_000));

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

        PostReadQueryService service = new PostReadQueryService(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                new PostSummaryAssembler(textCodec()),
                new PostDetailAssembler(textCodec()),
                new RecentUserCommentAssembler(textCodec())
        );

        List<RecentUserCommentView> items = service.listRecentCommentsByUser(7, 0, 3);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).postId()).isEqualTo(202);
        assertThat(items.get(0).postTitle()).isEqualTo("<second>");
        assertThat(items.get(0).content()).isEqualTo("<reply>");
        assertThat(items.get(1).postId()).isEqualTo(201);
        assertThat(items.get(1).postTitle()).isEqualTo("<first>");
    }

    @Test
    void listRecentCommentsByUserShouldSkipBrokenReplyTargetsInsteadOfFailingWholeFeed() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        Comment brokenReply = new Comment();
        brokenReply.setId(41);
        brokenReply.setUserId(7);
        brokenReply.setEntityType(CommentService.ENTITY_TYPE_COMMENT);
        brokenReply.setEntityId(404);
        brokenReply.setContent("&lt;reply&gt;");
        brokenReply.setCreateTime(new Date(3_000));

        Comment direct = new Comment();
        direct.setId(42);
        direct.setUserId(7);
        direct.setEntityType(CommentService.ENTITY_TYPE_POST);
        direct.setEntityId(201);
        direct.setContent("&lt;direct&gt;");
        direct.setCreateTime(new Date(2_000));

        DiscussPost firstPost = new DiscussPost();
        firstPost.setId(201);
        firstPost.setTitle("&lt;first&gt;");

        when(commentService.listRecentCommentsByUser(7, 0, 3)).thenReturn(List.of(brokenReply, direct));
        when(commentService.getById(404)).thenThrow(new BusinessException(ContentErrorCode.COMMENT_NOT_FOUND));
        when(postService.getById(201)).thenReturn(firstPost);

        PostReadQueryService service = new PostReadQueryService(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                new PostSummaryAssembler(textCodec()),
                new PostDetailAssembler(textCodec()),
                new RecentUserCommentAssembler(textCodec())
        );

        List<RecentUserCommentView> items = service.listRecentCommentsByUser(7, 0, 3);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).id()).isEqualTo(42);
        assertThat(items.get(0).postId()).isEqualTo(201);
    }

    @Test
    void listPostsByIdsShouldPreserveRequestedOrder() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

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
        lastActivity.setCreateTime(new Date(4_000));

        when(postService.listPostsByIds(List.of(12, 9))).thenReturn(List.of(first, second));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(12, 9))).thenReturn(Map.of(12, lastActivity));
        when(tagService.getTagsByPostIds(List.of(12, 9))).thenReturn(Map.of(12, List.of("java"), 9, List.of("spring")));

        PostReadQueryService service = new PostReadQueryService(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                new PostSummaryAssembler(textCodec()),
                new PostDetailAssembler(textCodec()),
                new RecentUserCommentAssembler(textCodec())
        );

        List<PostSummaryView> items = service.listPostsByIds(List.of(12, 9));

        assertThat(items).hasSize(2);
        assertThat(items.get(0).id()).isEqualTo(12);
        assertThat(items.get(1).id()).isEqualTo(9);
        assertThat(items.get(0).commentCount()).isEqualTo(4);
    }

    private ContentTextCodec textCodec() {
        return new ContentTextCodec(new ContentRenderProperties());
    }
}
