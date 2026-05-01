package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.application.result.RecentUserCommentResult;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.SubscriptionRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.content.application.PostDetailAssembler;
import com.nowcoder.community.content.application.PostSummaryAssembler;
import com.nowcoder.community.content.application.RecentUserCommentAssembler;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.content.application.LikeQueryPort;
import com.nowcoder.community.content.application.ContentTextCodec;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostReadApplicationServiceTest {

    @Test
    void listPostsShouldAssemblePostSummariesWithoutHttpDtoProjection() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        UUID postId = uuid(10);
        UUID authorUserId = uuid(2);
        UUID lastReplyUserId = uuid(3);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(authorUserId);
        post.setTitle("&lt;title&gt;");
        post.setContent("&lt;content&gt;");
        post.setCommentCount(3);

        Comment lastActivity = new Comment();
        lastActivity.setUserId(lastReplyUserId);
        lastActivity.setCreateTime(new Date(2_000));
        lastActivity.setContent("&lt;latest reply&gt;");

        when(postService.listPosts(0, 10, PostContentRepository.ORDER_LATEST, null, null)).thenReturn(List.of(post));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(postId))).thenReturn(Map.of(postId, lastActivity));
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("java")));

        PostReadApplicationService service = new PostReadApplicationService(
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

        List<PostSummaryResult> views = service.listPosts(null, "latest", null, null, false, 0, 10);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo(postId);
        assertThat(views.get(0).title()).isEqualTo("<title>");
        assertThat(views.get(0).tags()).containsExactly("java");
        assertThat(views.get(0).lastActivityTime()).isEqualTo(lastActivity.getCreateTime());
        assertThat(views.get(0).lastReplyPreview()).isEqualTo("<latest reply>");
    }

    @Test
    void getPostDetailShouldAssembleTagsLikesAndBookmarkStateWithoutHttpDtoProjection() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        UUID currentUserId = uuid(7);
        UUID postId = uuid(10);
        UUID authorUserId = uuid(8);
        UUID categoryId = uuid(3);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(authorUserId);
        post.setTitle("&lt;title&gt;");
        post.setContent("&lt;body&gt;");
        post.setType(1);
        post.setStatus(0);
        post.setCreateTime(new Date(1_000));
        post.setUpdateTime(new Date(2_000));
        post.setEditCount(2);
        post.setCommentCount(5);
        post.setScore(12.5);
        post.setCategoryId(categoryId);

        when(postService.getById(postId)).thenReturn(post);
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("java", "spring")));
        when(likeQueryService.countPostLikes(postId)).thenReturn(9L);
        when(likeQueryService.hasLikedPost(currentUserId, postId)).thenReturn(true);
        when(bookmarkService.hasBookmarked(currentUserId, postId)).thenReturn(true);

        PostReadApplicationService service = new PostReadApplicationService(
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

        PostDetailResult detail = service.getPostDetail(currentUserId, postId);

        assertThat(detail.id()).isEqualTo(postId);
        assertThat(detail.title()).isEqualTo("<title>");
        assertThat(detail.content()).isEqualTo("<body>");
        assertThat(detail.tags()).containsExactly("java", "spring");
        assertThat(detail.likeCount()).isEqualTo(9L);
        assertThat(detail.liked()).isTrue();
        assertThat(detail.bookmarked()).isTrue();
    }

    @Test
    void listPostsByUserShouldAssembleRecentSummaries() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        UUID userId = uuid(7);
        UUID firstPostId = uuid(21);
        UUID secondPostId = uuid(22);
        UUID lastReplyUserId = uuid(11);

        DiscussPost first = new DiscussPost();
        first.setId(firstPostId);
        first.setUserId(userId);
        first.setTitle("&lt;first&gt;");
        first.setCommentCount(5);

        DiscussPost second = new DiscussPost();
        second.setId(secondPostId);
        second.setUserId(userId);
        second.setTitle("&lt;second&gt;");
        second.setCommentCount(1);

        Comment lastActivity = new Comment();
        lastActivity.setUserId(lastReplyUserId);
        lastActivity.setCreateTime(new Date(3_000));
        lastActivity.setContent("&lt;newest&gt;");

        when(postService.listPostsByUser(userId, 0, 3)).thenReturn(List.of(first, second));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(firstPostId, secondPostId))).thenReturn(Map.of(firstPostId, lastActivity));
        when(tagService.getTagsByPostIds(List.of(firstPostId, secondPostId))).thenReturn(Map.of(firstPostId, List.of("java"), secondPostId, List.of("spring")));

        PostReadApplicationService service = new PostReadApplicationService(
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

        List<PostSummaryResult> items = service.listPostsByUser(userId, 0, 3);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).userId()).isEqualTo(userId);
        assertThat(items.get(0).title()).isEqualTo("<first>");
        assertThat(items.get(0).tags()).containsExactly("java");
        assertThat(items.get(0).lastReplyUserId()).isEqualTo(lastReplyUserId);
        assertThat(items.get(0).lastReplyPreview()).isEqualTo("<newest>");
        assertThat(items.get(1).tags()).containsExactly("spring");
    }

    @Test
    void listRecentCommentsByUserShouldResolveDirectCommentsAndReplies() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        UUID userId = uuid(7);
        UUID directCommentId = uuid(31);
        UUID replyCommentId = uuid(32);
        UUID parentCommentId = uuid(88);
        UUID firstPostId = uuid(201);
        UUID secondPostId = uuid(202);

        Comment direct = new Comment();
        direct.setId(directCommentId);
        direct.setUserId(userId);
        direct.setEntityType(CommentContentRepository.ENTITY_TYPE_POST);
        direct.setEntityId(firstPostId);
        direct.setContent("&lt;direct&gt;");
        direct.setCreateTime(new Date(2_000));

        Comment reply = new Comment();
        reply.setId(replyCommentId);
        reply.setUserId(userId);
        reply.setEntityType(CommentContentRepository.ENTITY_TYPE_COMMENT);
        reply.setEntityId(parentCommentId);
        reply.setContent("&lt;reply&gt;");
        reply.setCreateTime(new Date(3_000));

        Comment parent = new Comment();
        parent.setId(parentCommentId);
        parent.setEntityType(CommentContentRepository.ENTITY_TYPE_POST);
        parent.setEntityId(secondPostId);

        DiscussPost firstPost = new DiscussPost();
        firstPost.setId(firstPostId);
        firstPost.setTitle("&lt;first&gt;");

        DiscussPost secondPost = new DiscussPost();
        secondPost.setId(secondPostId);
        secondPost.setTitle("&lt;second&gt;");

        when(commentService.listRecentCommentsByUser(userId, 0, 3)).thenReturn(List.of(reply, direct));
        when(commentService.getById(parentCommentId)).thenReturn(parent);
        when(postService.getById(firstPostId)).thenReturn(firstPost);
        when(postService.getById(secondPostId)).thenReturn(secondPost);

        PostReadApplicationService service = new PostReadApplicationService(
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

        List<RecentUserCommentResult> items = service.listRecentCommentsByUser(userId, 0, 3);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).postId()).isEqualTo(secondPostId);
        assertThat(items.get(0).postTitle()).isEqualTo("<second>");
        assertThat(items.get(0).content()).isEqualTo("<reply>");
        assertThat(items.get(1).postId()).isEqualTo(firstPostId);
        assertThat(items.get(1).postTitle()).isEqualTo("<first>");
    }

    @Test
    void listRecentCommentsByUserShouldSkipBrokenReplyTargetsInsteadOfFailingWholeFeed() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        UUID userId = uuid(7);
        UUID brokenReplyId = uuid(41);
        UUID directCommentId = uuid(42);
        UUID missingParentCommentId = uuid(404);
        UUID postId = uuid(201);

        Comment brokenReply = new Comment();
        brokenReply.setId(brokenReplyId);
        brokenReply.setUserId(userId);
        brokenReply.setEntityType(CommentContentRepository.ENTITY_TYPE_COMMENT);
        brokenReply.setEntityId(missingParentCommentId);
        brokenReply.setContent("&lt;reply&gt;");
        brokenReply.setCreateTime(new Date(3_000));

        Comment direct = new Comment();
        direct.setId(directCommentId);
        direct.setUserId(userId);
        direct.setEntityType(CommentContentRepository.ENTITY_TYPE_POST);
        direct.setEntityId(postId);
        direct.setContent("&lt;direct&gt;");
        direct.setCreateTime(new Date(2_000));

        DiscussPost firstPost = new DiscussPost();
        firstPost.setId(postId);
        firstPost.setTitle("&lt;first&gt;");

        when(commentService.listRecentCommentsByUser(userId, 0, 3)).thenReturn(List.of(brokenReply, direct));
        when(commentService.getById(missingParentCommentId)).thenThrow(new BusinessException(ContentErrorCode.COMMENT_NOT_FOUND));
        when(postService.getById(postId)).thenReturn(firstPost);

        PostReadApplicationService service = new PostReadApplicationService(
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

        List<RecentUserCommentResult> items = service.listRecentCommentsByUser(userId, 0, 3);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).id()).isEqualTo(directCommentId);
        assertThat(items.get(0).postId()).isEqualTo(postId);
    }

    @Test
    void listPostsByIdsShouldPreserveRequestedOrderWithoutHttpDtoProjection() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        UUID firstPostId = uuid(12);
        UUID secondPostId = uuid(9);
        UUID firstAuthorId = uuid(2);
        UUID secondAuthorId = uuid(3);
        UUID lastReplyUserId = uuid(5);
        List<UUID> requestedPostIds = List.of(firstPostId, secondPostId);

        DiscussPost first = new DiscussPost();
        first.setId(firstPostId);
        first.setUserId(firstAuthorId);
        first.setTitle("&lt;first&gt;");
        first.setCommentCount(4);

        DiscussPost second = new DiscussPost();
        second.setId(secondPostId);
        second.setUserId(secondAuthorId);
        second.setTitle("&lt;second&gt;");
        second.setCommentCount(2);

        Comment lastActivity = new Comment();
        lastActivity.setUserId(lastReplyUserId);
        lastActivity.setCreateTime(new Date(3_000));
        lastActivity.setContent("&lt;newest&gt;");

        when(postService.listPostsByIds(requestedPostIds)).thenReturn(List.of(first, second));
        when(commentService.getLatestPostActivitiesByPostIds(requestedPostIds)).thenReturn(Map.of(firstPostId, lastActivity));
        when(tagService.getTagsByPostIds(requestedPostIds)).thenReturn(Map.of(firstPostId, List.of("java"), secondPostId, List.of("spring")));

        PostReadApplicationService service = new PostReadApplicationService(
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

        List<PostSummaryResult> items = service.listPostsByIds(requestedPostIds);

        assertThat(items).hasSize(2);
        assertThat(items).extracting(PostSummaryResult::id).containsExactly(firstPostId, secondPostId);
        assertThat(items.get(0).userId()).isEqualTo(firstAuthorId);
        assertThat(items.get(0).lastReplyUserId()).isEqualTo(lastReplyUserId);
        assertThat(items.get(1).userId()).isEqualTo(secondAuthorId);
    }

    private static ContentTextCodec textCodec() {
        return new ContentTextCodec(new ContentRenderProperties());
    }
}
