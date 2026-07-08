package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.application.result.RecentUserCommentResult;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.domain.repository.SubscriptionRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.content.application.PostDetailAssembler;
import com.nowcoder.community.content.application.PostSummaryAssembler;
import com.nowcoder.community.content.application.RecentUserCommentAssembler;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.content.application.LikeQueryPort;
import com.nowcoder.community.content.application.ContentTextCodec;
import com.nowcoder.community.content.infrastructure.text.SpringHtmlContentTextCodec;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        UUID postId = uuid(10);
        UUID authorUserId = uuid(2);
        UUID lastReplyUserId = uuid(3);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(authorUserId);
        post.setTitle("&lt;title&gt;");
        post.setCommentCount(3);

        Comment lastActivity = new Comment();
        lastActivity.setUserId(lastReplyUserId);
        lastActivity.setCreateTime(new Date(2_000));
        lastActivity.setContent("&lt;latest reply&gt;");

        when(postService.listPosts(0, 10, PostContentRepository.ORDER_LATEST, null, null)).thenReturn(List.of(post));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(postId))).thenReturn(Map.of(postId, lastActivity));
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("java")));
        when(blockRepository.listByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of(paragraphBlock(postId, "&lt;content&gt;"))));

        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache
        );

        List<PostSummaryResult> views = service.listPosts(null, "latest", null, null, false, 0, 10);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo(postId);
        assertThat(views.get(0).title()).isEqualTo("<title>");
        assertThat(views.get(0).preview()).isEqualTo("<content>");
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
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterApplicationService postCounterApplicationService = mock(PostCounterApplicationService.class);
        UUID currentUserId = uuid(7);
        UUID postId = uuid(10);
        UUID authorUserId = uuid(8);
        UUID categoryId = uuid(3);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(authorUserId);
        post.setTitle("&lt;title&gt;");
        post.setType(1);
        post.setStatus(0);
        post.setCreateTime(new Date(1_000));
        post.setUpdateTime(new Date(2_000));
        post.setEditCount(2);
        post.setCommentCount(5);
        post.setScore(12.5);
        post.setCategoryId(categoryId);

        when(postService.getById(postId)).thenReturn(post);
        when(blockRepository.listByPostId(postId)).thenReturn(List.of(paragraphBlock(postId, "&lt;body&gt;")));
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("java", "spring")));
        when(postCounterApplicationService.read(postId)).thenReturn(new PostCounterSnapshot(postId, 3L, 9L, 5L, 2L, 12.5));
        when(likeQueryService.hasLikedPost(currentUserId, postId)).thenReturn(true);
        when(bookmarkService.hasBookmarked(currentUserId, postId)).thenReturn(true);

        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache,
                postCounterApplicationService
        );

        PostDetailResult detail = service.getPostDetail(currentUserId, postId);

        assertThat(detail.id()).isEqualTo(postId);
        assertThat(detail.title()).isEqualTo("<title>");
        assertThat(detail.blocks()).singleElement().satisfies(block -> {
            assertThat(block.type()).isEqualTo("paragraph");
            assertThat(block.text()).isEqualTo("<body>");
        });
        assertThat(detail.tags()).containsExactly("java", "spring");
        assertThat(detail.likeCount()).isEqualTo(9L);
        assertThat(detail.liked()).isTrue();
        assertThat(detail.bookmarked()).isTrue();
    }

    @Test
    void detailShouldReturnCachedShellBeforeFallingBackToRepositories() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        UUID postId = uuid(100);

        when(postDetailCache.get(postId)).thenReturn(detail(postId));

        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache
        );

        PostDetailResult result = service.getPostDetail(null, postId);

        assertThat(result.id()).isEqualTo(postId);
        verifyNoInteractions(postService, blockRepository, tagService, likeQueryService, bookmarkService);
    }

    @Test
    void detailShouldOverlayCounterSnapshotOnCacheHit() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterApplicationService postCounterApplicationService = mock(PostCounterApplicationService.class);
        UUID postId = uuid(102);

        when(postDetailCache.get(postId)).thenReturn(detail(postId));
        when(postCounterApplicationService.read(postId)).thenReturn(new PostCounterSnapshot(postId, 15L, 9L, 7L, 0L, 33.5));

        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache,
                postCounterApplicationService
        );

        PostDetailResult result = service.getPostDetail(null, postId);

        assertThat(result.likeCount()).isEqualTo(9L);
        assertThat(result.commentCount()).isEqualTo(7);
        assertThat(result.score()).isEqualTo(33.5);
        verify(postCounterApplicationService).read(postId);
        verifyNoInteractions(postService, blockRepository, tagService, likeQueryService, bookmarkService);
    }

    @Test
    void detailShouldOverlayViewerSpecificStateOnAuthenticatedCacheHit() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        UUID currentUserId = uuid(700);
        UUID postId = uuid(101);

        when(postDetailCache.get(postId)).thenReturn(detail(postId));
        when(likeQueryService.hasLikedPost(currentUserId, postId)).thenReturn(true);
        when(bookmarkService.hasBookmarked(currentUserId, postId)).thenReturn(true);

        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache
        );

        PostDetailResult result = service.getPostDetail(currentUserId, postId);

        assertThat(result.id()).isEqualTo(postId);
        assertThat(result.liked()).isTrue();
        assertThat(result.bookmarked()).isTrue();
        verify(postDetailCache).get(postId);
        verify(likeQueryService).hasLikedPost(currentUserId, postId);
        verify(bookmarkService).hasBookmarked(currentUserId, postId);
        verifyNoInteractions(postService, blockRepository, tagService, mediaAssetRepository);
    }

    @Test
    void detailShouldWriteViewerNeutralShellIntoCacheOnMiss() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        UUID currentUserId = uuid(7);
        UUID postId = uuid(10);
        UUID authorUserId = uuid(8);
        UUID categoryId = uuid(3);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(authorUserId);
        post.setTitle("&lt;title&gt;");
        post.setType(1);
        post.setStatus(0);
        post.setCreateTime(new Date(1_000));
        post.setUpdateTime(new Date(2_000));
        post.setEditCount(2);
        post.setCommentCount(5);
        post.setScore(12.5);
        post.setCategoryId(categoryId);

        when(postService.getById(postId)).thenReturn(post);
        when(blockRepository.listByPostId(postId)).thenReturn(List.of(paragraphBlock(postId, "&lt;body&gt;")));
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("java", "spring")));
        when(likeQueryService.countPostLikes(postId)).thenReturn(9L);
        when(likeQueryService.hasLikedPost(currentUserId, postId)).thenReturn(true);
        when(bookmarkService.hasBookmarked(currentUserId, postId)).thenReturn(true);

        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache
        );

        PostDetailResult result = service.getPostDetail(currentUserId, postId);

        assertThat(result.liked()).isTrue();
        assertThat(result.bookmarked()).isTrue();
        verify(postDetailCache).put(eq(postId), argThat(detail ->
                detail != null
                        && detail.id().equals(postId)
                        && !detail.liked()
                        && !detail.bookmarked()));
    }

    @Test
    void getPostDetailShouldLoadShellThroughSingleFlightOnCacheMiss() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterApplicationService postCounterApplicationService = mock(PostCounterApplicationService.class);
        HotPathSingleFlight singleFlight = mock(HotPathSingleFlight.class);
        UUID postId = uuid(10);
        UUID authorUserId = uuid(8);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(authorUserId);
        post.setTitle("&lt;title&gt;");
        post.setCreateTime(new Date(1_000));

        when(postDetailCache.get(postId)).thenReturn(null);
        when(singleFlight.execute(eq("post_detail"), eq(postId.toString()), any(), any(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<PostDetailResult> loader = invocation.getArgument(3);
                    return loader.get();
                });
        when(postService.getById(postId)).thenReturn(post);
        when(blockRepository.listByPostId(postId)).thenReturn(List.of(paragraphBlock(postId, "&lt;body&gt;")));
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("java")));
        when(postCounterApplicationService.read(postId)).thenReturn(PostCounterSnapshot.empty());

        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache,
                postCounterApplicationService,
                singleFlight
        );

        PostDetailResult result = service.getPostDetail(null, postId);

        assertThat(result.id()).isEqualTo(postId);
        verify(singleFlight).execute(eq("post_detail"), eq(postId.toString()), any(), any(), any());
    }

    @Test
    void detailShouldFailOpenToSourceWhenCacheReadFails() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterApplicationService postCounterApplicationService = mock(PostCounterApplicationService.class);
        UUID currentUserId = uuid(7);
        UUID postId = uuid(120);
        UUID authorUserId = uuid(8);
        UUID categoryId = uuid(3);
        DiscussPost post = post(postId, authorUserId, categoryId);

        when(postDetailCache.get(postId)).thenThrow(new RuntimeException("redis read failed"));
        when(postService.getById(postId)).thenReturn(post);
        when(blockRepository.listByPostId(postId)).thenReturn(List.of(paragraphBlock(postId, "&lt;body&gt;")));
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("java", "spring")));
        when(postCounterApplicationService.read(postId)).thenReturn(new PostCounterSnapshot(postId, 3L, 9L, 7L, 0L, 33.5));
        when(likeQueryService.hasLikedPost(currentUserId, postId)).thenReturn(true);
        when(bookmarkService.hasBookmarked(currentUserId, postId)).thenReturn(true);
        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache,
                postCounterApplicationService
        );

        PostDetailResult result = service.getPostDetail(currentUserId, postId);

        assertThat(result.id()).isEqualTo(postId);
        assertThat(result.title()).isEqualTo("<title>");
        assertThat(result.blocks()).singleElement().satisfies(block -> assertThat(block.text()).isEqualTo("<body>"));
        assertThat(result.tags()).containsExactly("java", "spring");
        assertThat(result.likeCount()).isEqualTo(9L);
        assertThat(result.commentCount()).isEqualTo(7);
        assertThat(result.score()).isEqualTo(33.5);
        assertThat(result.liked()).isTrue();
        assertThat(result.bookmarked()).isTrue();
    }

    @Test
    void detailShouldIgnoreCacheWriteFailureOnMiss() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterApplicationService postCounterApplicationService = mock(PostCounterApplicationService.class);
        UUID currentUserId = uuid(7);
        UUID postId = uuid(121);
        UUID authorUserId = uuid(8);
        UUID categoryId = uuid(3);
        DiscussPost post = post(postId, authorUserId, categoryId);

        when(postDetailCache.get(postId)).thenReturn(null);
        org.mockito.Mockito.doThrow(new RuntimeException("redis write failed"))
                .when(postDetailCache).put(eq(postId), any(PostDetailResult.class));
        when(postService.getById(postId)).thenReturn(post);
        when(blockRepository.listByPostId(postId)).thenReturn(List.of(paragraphBlock(postId, "&lt;body&gt;")));
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("java", "spring")));
        when(postCounterApplicationService.read(postId)).thenReturn(new PostCounterSnapshot(postId, 3L, 9L, 7L, 0L, 33.5));
        when(likeQueryService.hasLikedPost(currentUserId, postId)).thenReturn(true);
        when(bookmarkService.hasBookmarked(currentUserId, postId)).thenReturn(true);
        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache,
                postCounterApplicationService
        );

        PostDetailResult result = service.getPostDetail(currentUserId, postId);

        assertThat(result.id()).isEqualTo(postId);
        assertThat(result.title()).isEqualTo("<title>");
        assertThat(result.blocks()).singleElement().satisfies(block -> assertThat(block.text()).isEqualTo("<body>"));
        assertThat(result.tags()).containsExactly("java", "spring");
        assertThat(result.likeCount()).isEqualTo(9L);
        assertThat(result.commentCount()).isEqualTo(7);
        assertThat(result.score()).isEqualTo(33.5);
        assertThat(result.liked()).isTrue();
        assertThat(result.bookmarked()).isTrue();
    }

    @Test
    void listPostsByUserShouldAssembleRecentSummaries() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
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

        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache
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
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        UUID userId = uuid(7);
        UUID directCommentId = uuid(31);
        UUID replyCommentId = uuid(32);
        UUID parentCommentId = uuid(88);
        UUID firstPostId = uuid(201);
        UUID secondPostId = uuid(202);

        Comment direct = new Comment();
        direct.setId(directCommentId);
        direct.setPostId(firstPostId);
        direct.setUserId(userId);
        direct.setContent("&lt;direct&gt;");
        direct.setCreateTime(new Date(2_000));

        Comment reply = new Comment();
        reply.setId(replyCommentId);
        reply.setPostId(secondPostId);
        reply.setRootCommentId(parentCommentId);
        reply.setParentCommentId(parentCommentId);
        reply.setUserId(userId);
        reply.setContent("&lt;reply&gt;");
        reply.setCreateTime(new Date(3_000));

        DiscussPost firstPost = new DiscussPost();
        firstPost.setId(firstPostId);
        firstPost.setTitle("&lt;first&gt;");

        DiscussPost secondPost = new DiscussPost();
        secondPost.setId(secondPostId);
        secondPost.setTitle("&lt;second&gt;");

        when(commentService.listRecentCommentsByUser(userId, 0, 3)).thenReturn(List.of(reply, direct));
        when(postService.getById(firstPostId)).thenReturn(firstPost);
        when(postService.getById(secondPostId)).thenReturn(secondPost);

        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache
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
    void listRecentCommentsByUserShouldSkipCommentsMissingPostReferenceInsteadOfFailingWholeFeed() {
        PostContentRepository postService = mock(PostContentRepository.class);
        CommentContentRepository commentService = mock(CommentContentRepository.class);
        LikeQueryPort likeQueryService = mock(LikeQueryPort.class);
        TagContentRepository tagService = mock(TagContentRepository.class);
        BookmarkRepository bookmarkService = mock(BookmarkRepository.class);
        SubscriptionRepository subscriptionService = mock(SubscriptionRepository.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        UUID userId = uuid(7);
        UUID brokenReplyId = uuid(41);
        UUID directCommentId = uuid(42);
        UUID postId = uuid(201);

        Comment brokenReply = new Comment();
        brokenReply.setId(brokenReplyId);
        brokenReply.setUserId(userId);
        brokenReply.setContent("&lt;reply&gt;");
        brokenReply.setCreateTime(new Date(3_000));

        Comment direct = new Comment();
        direct.setId(directCommentId);
        direct.setPostId(postId);
        direct.setUserId(userId);
        direct.setContent("&lt;direct&gt;");
        direct.setCreateTime(new Date(2_000));

        DiscussPost firstPost = new DiscussPost();
        firstPost.setId(postId);
        firstPost.setTitle("&lt;first&gt;");

        when(commentService.listRecentCommentsByUser(userId, 0, 3)).thenReturn(List.of(brokenReply, direct));
        when(postService.getById(postId)).thenReturn(firstPost);

        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache
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
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostMediaAssetRepository mediaAssetRepository = mock(PostMediaAssetRepository.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
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

        PostReadApplicationService service = service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache
        );

        List<PostSummaryResult> items = service.listPostsByIds(requestedPostIds);

        assertThat(items).hasSize(2);
        assertThat(items).extracting(PostSummaryResult::id).containsExactly(firstPostId, secondPostId);
        assertThat(items.get(0).userId()).isEqualTo(firstAuthorId);
        assertThat(items.get(0).lastReplyUserId()).isEqualTo(lastReplyUserId);
        assertThat(items.get(1).userId()).isEqualTo(secondAuthorId);
    }

    private static ContentTextCodec textCodec() {
        return new SpringHtmlContentTextCodec();
    }

    private static PostReadApplicationService service(
            PostContentRepository postService,
            CommentContentRepository commentService,
            LikeQueryPort likeQueryService,
            TagContentRepository tagService,
            BookmarkRepository bookmarkService,
            SubscriptionRepository subscriptionService,
            PostContentBlockRepository blockRepository,
            PostMediaAssetRepository mediaAssetRepository,
            PostDetailCache postDetailCache
    ) {
        PostCounterApplicationService postCounterApplicationService = mock(PostCounterApplicationService.class);
        when(postCounterApplicationService.read(any())).thenReturn(PostCounterSnapshot.empty());
        return service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache,
                postCounterApplicationService
        );
    }

    private static PostReadApplicationService service(
            PostContentRepository postService,
            CommentContentRepository commentService,
            LikeQueryPort likeQueryService,
            TagContentRepository tagService,
            BookmarkRepository bookmarkService,
            SubscriptionRepository subscriptionService,
            PostContentBlockRepository blockRepository,
            PostMediaAssetRepository mediaAssetRepository,
            PostDetailCache postDetailCache,
            PostCounterApplicationService postCounterApplicationService
    ) {
        return service(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache,
                postCounterApplicationService,
                loaderSingleFlight()
        );
    }

    private static PostReadApplicationService service(
            PostContentRepository postService,
            CommentContentRepository commentService,
            LikeQueryPort likeQueryService,
            TagContentRepository tagService,
            BookmarkRepository bookmarkService,
            SubscriptionRepository subscriptionService,
            PostContentBlockRepository blockRepository,
            PostMediaAssetRepository mediaAssetRepository,
            PostDetailCache postDetailCache,
            PostCounterApplicationService postCounterApplicationService,
            HotPathSingleFlight hotPathSingleFlight
    ) {
        return new PostReadApplicationService(
                postService,
                commentService,
                likeQueryService,
                tagService,
                bookmarkService,
                subscriptionService,
                postCounterApplicationService,
                blockRepository,
                mediaAssetRepository,
                postDetailCache,
                new PostContentBlockTextProjector(),
                textCodec(),
                new PostSummaryAssembler(textCodec()),
                new PostDetailAssembler(textCodec()),
                new RecentUserCommentAssembler(textCodec()),
                new ContentHotPathProperties(),
                hotPathSingleFlight
        );
    }

    private static PostContentBlock paragraphBlock(UUID postId, String text) {
        return new PostContentBlock(uuid(501), postId, 0, "paragraph", text, null, "", "", "", null);
    }

    private static DiscussPost post(UUID postId, UUID authorUserId, UUID categoryId) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(authorUserId);
        post.setTitle("&lt;title&gt;");
        post.setType(1);
        post.setStatus(0);
        post.setCreateTime(new Date(1_000));
        post.setUpdateTime(new Date(2_000));
        post.setEditCount(2);
        post.setCommentCount(5);
        post.setScore(12.5);
        post.setCategoryId(categoryId);
        return post;
    }

    private static HotPathSingleFlight loaderSingleFlight() {
        return new HotPathSingleFlight() {
            @Override
            public <T> T execute(String scope, String key, java.time.Duration ttl, java.util.function.Supplier<T> loader, java.util.function.Supplier<T> fallbackWhenBusy) {
                return loader.get();
            }
        };
    }

    private static PostDetailResult detail(UUID postId) {
        return new PostDetailResult(
                postId,
                uuid(2),
                "<title>",
                List.of(),
                0,
                0,
                new Date(1_000),
                new Date(2_000),
                0,
                0,
                0.0,
                uuid(3),
                List.of(),
                0L,
                false,
                false
        );
    }
}
