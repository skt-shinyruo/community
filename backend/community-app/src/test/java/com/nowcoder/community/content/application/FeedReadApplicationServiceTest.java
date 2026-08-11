package com.nowcoder.community.content.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.content.application.result.FeedPageResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.nowcoder.community.content.support.CommentTestBuilder.aComment;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedReadApplicationServiceTest {

    @Test
    void listGlobalHotFeedShouldReadOrderedProjectionFromFeedCacheAndEmitNextCursor() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        UUID firstPostId = uuid(1);
        UUID secondPostId = uuid(2);
        UUID extraPostId = uuid(3);
        DiscussPost firstPost = post(firstPostId, "<first>");
        firstPost.setScore(20.0);
        DiscussPost secondPost = post(secondPostId, "<second>");
        secondPost.setScore(10.0);
        DiscussPost extraPost = post(extraPostId, "<extra>");
        extraPost.setScore(5.0);

        when(postFeedCache.readGlobalHotProjection("", 2)).thenReturn(new PostFeedCache.HotProjectionPage(
                List.of(projectionOf(firstPost), projectionOf(secondPost)),
                1L,
                true
        ));
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");
        when(postContentRepository.listPostsByIds(List.of(firstPostId, secondPostId)))
                .thenReturn(List.of(firstPost, secondPost));
        when(postContentRepository.listPosts(0, 2, 3, PostContentRepository.ORDER_HOT, null, null))
                .thenReturn(List.of(firstPost, secondPost, extraPost));
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(firstPostId, secondPostId)))
                .thenReturn(Map.of(firstPostId, lastActivity(uuid(11), "<reply>")));
        when(tagContentRepository.getTagsByPostIds(List.of(firstPostId, secondPostId)))
                .thenReturn(Map.of(firstPostId, List.of("java"), secondPostId, List.of("spring")));
        when(postContentBlockRepository.listByPostIds(List.of(firstPostId, secondPostId)))
                .thenReturn(Map.of(
                        firstPostId, List.of(paragraphBlock(firstPostId, "<body-1>")),
                        secondPostId, List.of(paragraphBlock(secondPostId, "<body-2>"))
                ));
        when(postContentBlockTextProjector.preview(List.of(paragraphBlock(firstPostId, "<body-1>")), 240)).thenReturn("<body-1>");
        when(postContentBlockTextProjector.preview(List.of(paragraphBlock(secondPostId, "<body-2>")), 240)).thenReturn("<body-2>");

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);
        FeedCursorCodec.CursorState next = feedCursorCodec.decode(result.nextCursor());

        assertThat(result.rankVersion()).isEqualTo("hot-v2");
        assertThat(next.page()).isEqualTo(1);
        assertThat(next.size()).isEqualTo(2);
        assertThat(result.items()).extracting(PostSummaryResult::id).containsExactly(firstPostId, secondPostId);
        assertThat(result.items().get(0).title()).isEqualTo("<first>");
        assertThat(result.items().get(0).lastReplyPreview()).isEqualTo("<reply>");
    }

    @Test
    void cacheHitAtMaximumCursorPageShouldNotEmitAnotherCursor() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(mock(ContentTextCodec.class));
        FeedCursorCodec.HotBoundary boundary = new FeedCursorCodec.HotBoundary(
                0, 60.0, new Date(6_000L), uuid(701));
        String cursor = feedCursorCodec.encodeHotPage(
                FeedCursorCodec.MAX_CURSOR_PAGE, 2, boundary, 17L);
        Date firstCreatedAt = new Date(5_000L);
        Date secondCreatedAt = new Date(4_000L);
        PostFeedCache.HotProjectionEntry first = new PostFeedCache.HotProjectionEntry(
                uuid(702), 0, 59.0, firstCreatedAt);
        PostFeedCache.HotProjectionEntry second = new PostFeedCache.HotProjectionEntry(
                uuid(703), 0, 58.0, secondCreatedAt);

        when(postFeedCache.readGlobalHotProjection(cursor, 2))
                .thenReturn(new PostFeedCache.HotProjectionPage(List.of(first, second), 17L, true));
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");
        when(postSummaryCache.getAll(List.of(first.postId(), second.postId()))).thenReturn(Map.of(
                first.postId(), summaryWithRank(first.postId(), "<first>", 0, 59.0, firstCreatedAt),
                second.postId(), summaryWithRank(second.postId(), "<second>", 0, 58.0, secondCreatedAt)
        ));
        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult result = service.listGlobalHotFeed(null, cursor, 2);

        assertThat(result.items()).extracting(PostSummaryResult::id)
                .containsExactly(first.postId(), second.postId());
        assertThat(result.nextCursor()).isEmpty();
        verifyNoInteractions(postContentRepository);
    }

    @Test
    void sqlFallbackAtMaximumCursorPageShouldTrimLookaheadWithoutEmittingCursor() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        FeedCursorCodec.HotBoundary boundary = new FeedCursorCodec.HotBoundary(
                0, 60.0, new Date(6_000L), uuid(711));
        String cursor = feedCursorCodec.encodeHotPage(
                FeedCursorCodec.MAX_CURSOR_PAGE, 2, boundary, 17L);
        DiscussPost first = post(uuid(712), "<first>");
        first.setScore(59.0);
        first.setCreateTime(new Date(5_000L));
        DiscussPost second = post(uuid(713), "<second>");
        second.setScore(58.0);
        second.setCreateTime(new Date(4_000L));
        DiscussPost lookahead = post(uuid(714), "<lookahead>");
        lookahead.setScore(57.0);
        lookahead.setCreateTime(new Date(3_000L));

        when(postFeedCache.readGlobalHotProjection(cursor, 2)).thenReturn(cacheMiss());
        when(postContentRepository.listHotPostsAfter(
                boundary.type(), boundary.score(), boundary.createTime(), boundary.postId(), 3, null
        )).thenReturn(List.of(first, second, lookahead));
        mockSummaryDependencies(
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postContentBlockTextProjector,
                List.of(first, second)
        );
        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult result = service.listGlobalHotFeed(null, cursor, 2);

        assertThat(result.items()).extracting(PostSummaryResult::id)
                .containsExactly(first.getId(), second.getId());
        assertThat(result.nextCursor()).isEmpty();
    }

    @Test
    void listBoardHotFeedShouldConfirmProjectionTailThroughRepository() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        UUID boardId = uuid(8);
        UUID postId = uuid(21);
        DiscussPost boardPost = post(postId, "<board-post>");
        boardPost.setCategoryId(boardId);

        when(postFeedCache.readBoardHotProjection(boardId, null, 2)).thenReturn(new PostFeedCache.HotProjectionPage(
                List.of(projectionOf(boardPost)),
                1L,
                false
        ));
        when(postContentRepository.listPosts(0, 2, 3, PostContentRepository.ORDER_HOT, boardId, null))
                .thenReturn(List.of(boardPost));
        when(postContentRepository.listPostsByIds(List.of(postId)))
                .thenReturn(List.of(boardPost));
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(postId))).thenReturn(Map.of());
        when(tagContentRepository.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("board")));
        when(postContentBlockRepository.listByPostIds(List.of(postId)))
                .thenReturn(Map.of(postId, List.of(paragraphBlock(postId, "<board-preview>"))));
        when(postContentBlockTextProjector.preview(List.of(paragraphBlock(postId, "<board-preview>")), 240)).thenReturn("<board-preview>");

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult result = service.listBoardHotFeed(null, boardId, null, 2);

        assertThat(result.nextCursor()).isEmpty();
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(postId);
            assertThat(item.tags()).containsExactly("board");
        });
    }

    @Test
    void listBoardHotFeedShouldFallbackWhenCachedSummaryNoLongerBelongsToRequestedBoard() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        UUID boardId = uuid(8);
        UUID staleBoardId = uuid(9);
        UUID postId = uuid(22);

        when(postFeedCache.readBoardHotProjection(boardId, null, 1)).thenReturn(new PostFeedCache.HotProjectionPage(
                List.of(new PostFeedCache.HotProjectionEntry(postId, 0, 0.0, new Date(1_000))),
                1L,
                true
        ));
        when(postSummaryCache.getAll(List.of(postId)))
                .thenReturn(Map.of(postId, summary(postId, "<stale-board>", staleBoardId)));
        when(postContentRepository.listPosts(0, 1, 2, PostContentRepository.ORDER_HOT, boardId, null))
                .thenReturn(List.of());

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult result = service.listBoardHotFeed(null, boardId, null, 1);

        assertThat(result.items()).isEmpty();
        verify(postContentRepository).listPosts(0, 1, 2, PostContentRepository.ORDER_HOT, boardId, null);
    }

    @Test
    void listGlobalHotFeedShouldContinueFromNextCursorWithoutSkippingRows() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        List<DiscussPost> allPosts = IntStream.rangeClosed(1, 4)
                .mapToObj(i -> {
                    DiscussPost post = post(uuid(i), "<post-" + i + ">");
                    post.setScore(100.0 - i);
                    return post;
                })
                .toList();

        when(postFeedCache.readGlobalHotProjection("", 2)).thenReturn(new PostFeedCache.HotProjectionPage(
                allPosts.subList(0, 2).stream().map(FeedReadApplicationServiceTest::projectionOf).toList(),
                1L,
                true
        ));
        when(postContentRepository.listPostsByIds(List.of(uuid(1), uuid(2))))
                .thenReturn(allPosts.subList(0, 2));
        when(postContentRepository.listPostsByIds(List.of(uuid(3), uuid(4))))
                .thenReturn(allPosts.subList(2, 4));
        when(postContentRepository.listPosts(0, 2, 3, PostContentRepository.ORDER_HOT, null, null))
                .thenReturn(allPosts.subList(0, 3));
        DiscussPost secondBoundary = allPosts.get(1);
        when(postContentRepository.listHotPostsAfter(
                secondBoundary.getType(),
                secondBoundary.getScore(),
                secondBoundary.getCreateTime(),
                secondBoundary.getId(),
                3,
                null
        )).thenReturn(allPosts.subList(2, 4));
        mockSummaryDependencies(commentContentRepository, tagContentRepository, postContentBlockRepository, postContentBlockTextProjector, allPosts);

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult firstPage = service.listGlobalHotFeed(null, "", 2);
        when(postFeedCache.readGlobalHotProjection(firstPage.nextCursor(), 2))
                .thenReturn(cacheMiss());
        FeedPageResult secondPage = service.listGlobalHotFeed(null, firstPage.nextCursor(), 2);

        assertThat(firstPage.items()).extracting(PostSummaryResult::title).containsExactly("<post-1>", "<post-2>");
        assertThat(secondPage.items()).extracting(PostSummaryResult::title).containsExactly("<post-3>", "<post-4>");
    }

    @Test
    void warmCachedPageShouldUseProjectionRankTupleWithoutDatabaseQuery() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);

        UUID pinnedId = uuid(90);
        UUID boundaryId = uuid(82);
        UUID nextId = uuid(81);
        UUID extraId = uuid(80);
        Date pinnedAt = new Date(5_000L);
        Date boundaryAt = new Date(4_000L);
        Date nextAt = new Date(3_000L);
        Date extraAt = new Date(2_000L);
        PostFeedCache.HotProjectionEntry pinned = new PostFeedCache.HotProjectionEntry(
                pinnedId, 1, 10.0, pinnedAt);
        PostFeedCache.HotProjectionEntry boundary = new PostFeedCache.HotProjectionEntry(
                boundaryId, 0, 50.0, boundaryAt);
        PostFeedCache.HotProjectionEntry next = new PostFeedCache.HotProjectionEntry(
                nextId, 0, 49.0, nextAt);
        PostFeedCache.HotProjectionEntry extra = new PostFeedCache.HotProjectionEntry(
                extraId, 0, 48.0, extraAt);

        when(postFeedCache.readGlobalHotProjection("", 2))
                .thenReturn(new PostFeedCache.HotProjectionPage(List.of(pinned, boundary), 17L, true));
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");
        when(postSummaryCache.getAll(List.of(pinnedId, boundaryId))).thenReturn(Map.of(
                pinnedId, summaryWithRank(pinnedId, "<cached-pinned>", 1, 10.0, pinnedAt),
                boundaryId, summaryWithRank(boundaryId, "<cached-boundary>", 0, 50.0, boundaryAt)
        ));

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult firstPage = service.listGlobalHotFeed(null, "", 2);
        FeedCursorCodec.CursorState cursor = feedCursorCodec.decode(firstPage.nextCursor());
        FeedCursorCodec.HotBoundary cachedBoundary = new FeedCursorCodec.HotBoundary(
                0, 50.0, boundaryAt, boundaryId);

        when(postFeedCache.readGlobalHotProjection(firstPage.nextCursor(), 2))
                .thenReturn(new PostFeedCache.HotProjectionPage(List.of(next, extra), 17L, true));
        when(postSummaryCache.getAll(List.of(nextId, extraId))).thenReturn(Map.of(
                nextId, summaryWithRank(nextId, "<cached-next>", 0, 49.0, nextAt),
                extraId, summaryWithRank(extraId, "<cached-extra>", 0, 48.0, extraAt)
        ));
        FeedPageResult secondPage = service.listGlobalHotFeed(null, firstPage.nextCursor(), 2);

        assertThat(firstPage.items()).extracting(PostSummaryResult::id).containsExactly(pinnedId, boundaryId);
        assertThat(firstPage.items().get(0).score()).isEqualTo(10.0);
        assertThat(firstPage.items().get(1).score()).isEqualTo(50.0);
        assertThat(cursor.hotBoundary()).isEqualTo(cachedBoundary);
        assertThat(cursor.projectionEpoch()).isEqualTo(17L);
        assertThat(secondPage.items()).extracting(PostSummaryResult::id).containsExactly(nextId, extraId);
        verify(postFeedCache).readGlobalHotProjection(firstPage.nextCursor(), 2);
        verifyNoInteractions(
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postContentBlockTextProjector
        );
    }

    @Test
    void projectionTupleMismatchShouldFallbackToRepository() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        UUID postId = uuid(91);
        Date createdAt = new Date(7_000L);
        DiscussPost current = post(postId, "<current>");
        current.setScore(90.0);
        current.setCreateTime(createdAt);

        when(postFeedCache.readGlobalHotProjection("", 2)).thenReturn(new PostFeedCache.HotProjectionPage(
                List.of(new PostFeedCache.HotProjectionEntry(postId, 0, 90.0, createdAt)),
                11L,
                true
        ));
        when(postSummaryCache.getAll(List.of(postId))).thenReturn(Map.of(
                postId, summaryWithRank(postId, "<stale>", 0, 99.0, createdAt)
        ));
        when(postContentRepository.listPosts(0, 2, 3, PostContentRepository.ORDER_HOT, null, null))
                .thenReturn(List.of(current));
        mockSummaryDependencies(
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postContentBlockTextProjector,
                List.of(current)
        );
        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(postId);
            assertThat(item.score()).isEqualTo(90.0);
        });
        verify(postContentRepository).listPosts(0, 2, 3, PostContentRepository.ORDER_HOT, null, null);
    }

    @Test
    void projectionEpochMismatchShouldContinueFromSqlBoundary() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        FeedCursorCodec.HotBoundary boundary = new FeedCursorCodec.HotBoundary(
                0, 50.0, new Date(5_000L), uuid(92));
        String cursor = feedCursorCodec.encodeHotPage(1, 2, boundary, 7L);
        DiscussPost next = post(uuid(93), "<next>");
        next.setScore(49.0);
        next.setCreateTime(new Date(4_000L));

        when(postFeedCache.readGlobalHotProjection(cursor, 2))
                .thenReturn(new PostFeedCache.HotProjectionPage(List.of(), 0L, false));
        when(postContentRepository.listHotPostsAfter(
                boundary.type(), boundary.score(), boundary.createTime(), boundary.postId(), 3, null
        )).thenReturn(List.of(next));
        mockSummaryDependencies(
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postContentBlockTextProjector,
                List.of(next)
        );
        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult result = service.listGlobalHotFeed(null, cursor, 2);

        assertThat(result.items()).extracting(PostSummaryResult::id).containsExactly(next.getId());
        verify(postContentRepository).listHotPostsAfter(
                boundary.type(), boundary.score(), boundary.createTime(), boundary.postId(), 3, null);
    }

    @Test
    void projectionTailShouldNotHideRowsBeyondPrewarmWindow() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        FeedCursorCodec.HotBoundary boundary = new FeedCursorCodec.HotBoundary(
                0, 60.0, new Date(6_000L), uuid(94));
        String cursor = feedCursorCodec.encodeHotPage(20, 2, boundary, 9L);
        DiscussPost first = post(uuid(95), "<first-after-warm-tail>");
        first.setScore(59.0);
        first.setCreateTime(new Date(5_000L));
        DiscussPost second = post(uuid(96), "<second-after-warm-tail>");
        second.setScore(58.0);
        second.setCreateTime(new Date(4_000L));
        DiscussPost third = post(uuid(97), "<database-only-row>");
        third.setScore(57.0);
        third.setCreateTime(new Date(3_000L));
        List<PostFeedCache.HotProjectionEntry> cachedTail = List.of(projectionOf(first), projectionOf(second));

        when(postFeedCache.readGlobalHotProjection(cursor, 2))
                .thenReturn(new PostFeedCache.HotProjectionPage(cachedTail, 9L, false));
        when(postContentRepository.listHotPostsAfter(
                boundary.type(), boundary.score(), boundary.createTime(), boundary.postId(), 3, null
        )).thenReturn(List.of(first, second, third));
        mockSummaryDependencies(
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postContentBlockTextProjector,
                List.of(first, second, third)
        );
        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult result = service.listGlobalHotFeed(null, cursor, 2);

        assertThat(result.items()).extracting(PostSummaryResult::id)
                .containsExactly(first.getId(), second.getId());
        assertThat(result.nextCursor()).isNotBlank();
        verify(postContentRepository).listHotPostsAfter(
                boundary.type(), boundary.score(), boundary.createTime(), boundary.postId(), 3, null);
    }

    @Test
    void listGlobalHotFeedShouldTreatInvalidCursorAsFirstPage() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        List<DiscussPost> allPosts = List.of(post(uuid(1), "<first>"), post(uuid(2), "<second>"));

        when(postFeedCache.readGlobalHotProjection("%%%not-a-cursor%%%", 2))
                .thenReturn(new PostFeedCache.HotProjectionPage(
                        allPosts.stream().map(FeedReadApplicationServiceTest::projectionOf).toList(),
                        1L,
                        true
                ));
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");
        when(postContentRepository.listPostsByIds(List.of(uuid(1), uuid(2))))
                .thenReturn(allPosts);
        mockSummaryDependencies(commentContentRepository, tagContentRepository, postContentBlockRepository, postContentBlockTextProjector, allPosts);

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        assertThatCode(() -> service.listGlobalHotFeed(null, "%%%not-a-cursor%%%", 2))
                .doesNotThrowAnyException();
        FeedPageResult result = service.listGlobalHotFeed(null, "%%%not-a-cursor%%%", 2);

        assertThat(result.items()).extracting(PostSummaryResult::title).containsExactly("<first>", "<second>");
    }

    @Test
    void listGlobalHotFeedShouldFallbackToRepositoryWhenFeedCacheIsCold() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        DiscussPost firstPost = post(uuid(31), "<first>");
        firstPost.setScore(91.0);
        DiscussPost secondPost = post(uuid(32), "<second>");
        secondPost.setScore(88.0);
        List<DiscussPost> posts = List.of(firstPost, secondPost);

        when(postFeedCache.readGlobalHotProjection("", 2)).thenReturn(cacheMiss());
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");
        when(postContentRepository.listPosts(0, 2, 3, PostContentRepository.ORDER_HOT, null, null))
                .thenReturn(posts);
        mockSummaryDependencies(commentContentRepository, tagContentRepository, postContentBlockRepository, postContentBlockTextProjector, posts);

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).extracting(PostSummaryResult::id).containsExactly(uuid(31), uuid(32));
        assertThat(result.nextCursor()).isEmpty();
        verify(postFeedCache).upsertGlobalHot(projectionOf(firstPost), "hot-v2", 7L, 3L);
        verify(postFeedCache).upsertGlobalHot(projectionOf(secondPost), "hot-v2", 7L, 3L);
    }

    @Test
    void repositoryFallbackShouldUseOneRowLookaheadThenContinueWithKeysetBoundary() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        DiscussPost first = post(uuid(71), "<first>");
        first.setScore(90.0);
        DiscussPost second = post(uuid(72), "<second>");
        second.setScore(80.0);
        DiscussPost third = post(uuid(73), "<third>");
        third.setScore(70.0);

        when(postFeedCache.readGlobalHotProjection("", 2)).thenReturn(cacheMiss());
        when(postContentRepository.listPosts(0, 2, 3, PostContentRepository.ORDER_HOT, null, null))
                .thenReturn(List.of(first, second, third));
        when(postContentRepository.listHotPostsAfter(
                second.getType(),
                second.getScore(),
                second.getCreateTime(),
                second.getId(),
                3,
                null
        )).thenReturn(List.of(third));
        mockSummaryDependencies(
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postContentBlockTextProjector,
                List.of(first, second, third)
        );

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult firstPage = service.listGlobalHotFeed(null, "", 2);
        FeedCursorCodec.CursorState next = feedCursorCodec.decode(firstPage.nextCursor());
        when(postFeedCache.readGlobalHotProjection(firstPage.nextCursor(), 2)).thenReturn(cacheMiss());
        FeedPageResult secondPage = service.listGlobalHotFeed(null, firstPage.nextCursor(), 2);

        assertThat(firstPage.items()).extracting(PostSummaryResult::id).containsExactly(first.getId(), second.getId());
        assertThat(next.page()).isEqualTo(1);
        assertThat(next.hotBoundary()).isEqualTo(new FeedCursorCodec.HotBoundary(
                second.getType(), second.getScore(), second.getCreateTime(), second.getId()
        ));
        assertThat(secondPage.items()).extracting(PostSummaryResult::id).containsExactly(third.getId());
        assertThat(secondPage.nextCursor()).isEmpty();
        verify(postContentRepository).listPosts(0, 2, 3, PostContentRepository.ORDER_HOT, null, null);
        verify(postContentRepository).listHotPostsAfter(
                second.getType(),
                second.getScore(),
                second.getCreateTime(),
                second.getId(),
                3,
                null
        );
    }

    @Test
    void listGlobalHotFeedShouldNotFallbackToRepositoryWhenLatestFallbackDisabled() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        ContentFeedPolicyProperties policyProperties = new ContentFeedPolicyProperties();
        policyProperties.setLatestFallbackEnabled(false);

        when(postFeedCache.readGlobalHotProjection("", 2)).thenReturn(cacheMiss());
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec,
                policyProperties
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isEmpty();
        assertThat(result.rankVersion()).isEqualTo("hot-v2");
        verifyNoInteractions(
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector
        );
    }

    @Test
    void listGlobalHotFeedShouldReturnConfiguredRankVersionWhenColdCacheWarmsFeed() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        ContentFeedPolicyProperties policyProperties = new ContentFeedPolicyProperties();
        policyProperties.setHotRankVersion("hot-v9");
        DiscussPost firstPost = post(uuid(61), "<first>");
        firstPost.setScore(123.0);
        List<DiscussPost> posts = List.of(firstPost);

        when(postFeedCache.readGlobalHotProjection("", 2)).thenReturn(cacheMiss());
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");
        when(postContentRepository.listPosts(0, 2, 3, PostContentRepository.ORDER_HOT, null, null))
                .thenReturn(posts);
        mockSummaryDependencies(commentContentRepository, tagContentRepository, postContentBlockRepository, postContentBlockTextProjector, posts);

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec,
                policyProperties
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.rankVersion()).isEqualTo("hot-v9");
        verify(postFeedCache).writeRankVersion("hot-v9");
        verify(postFeedCache).upsertGlobalHot(projectionOf(firstPost), "hot-v9", 7L, 3L);
    }

    @Test
    void listBoardHotFeedShouldFallbackToRepositoryWhenBoardFeedCacheIsCold() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        UUID boardId = uuid(41);
        DiscussPost boardPost = post(uuid(42), "<board-post>");
        boardPost.setCategoryId(boardId);
        boardPost.setScore(77.0);

        when(postFeedCache.readBoardHotProjection(boardId, "", 2)).thenReturn(cacheMiss());
        when(postContentRepository.listPosts(0, 2, 3, PostContentRepository.ORDER_HOT, boardId, null))
                .thenReturn(List.of(boardPost));
        mockSummaryDependencies(
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postContentBlockTextProjector,
                List.of(boardPost)
        );

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult result = service.listBoardHotFeed(null, boardId, "", 2);

        assertThat(result.items()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(uuid(42)));
        assertThat(result.nextCursor()).isEmpty();
        verify(postFeedCache).upsertBoardHot(boardId, projectionOf(boardPost), "hot-v2", 7L, 3L);
    }

    @Test
    void globalFeedShouldBackfillMissingSummariesIntoCache() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        UUID firstPostId = uuid(1);
        UUID secondPostId = uuid(2);
        DiscussPost firstPost = post(firstPostId, "<first>");
        DiscussPost secondPost = post(secondPostId, "<second>");

        when(postFeedCache.readGlobalHotProjection("", 2)).thenReturn(new PostFeedCache.HotProjectionPage(
                List.of(projectionOf(firstPost), projectionOf(secondPost)),
                1L,
                true
        ));
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");
        when(postSummaryCache.getAll(List.of(firstPostId, secondPostId)))
                .thenReturn(Map.of(firstPostId, summary(firstPostId, "<cached>")));
        when(postContentRepository.listPostsByIds(List.of(secondPostId)))
                .thenReturn(List.of(secondPost));
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(secondPostId))).thenReturn(Map.of());
        when(tagContentRepository.getTagsByPostIds(List.of(secondPostId))).thenReturn(Map.of(secondPostId, List.of("spring")));
        when(postContentBlockRepository.listByPostIds(List.of(secondPostId)))
                .thenReturn(Map.of(secondPostId, List.of(paragraphBlock(secondPostId, "<body-2>"))));
        when(postContentBlockTextProjector.preview(List.of(paragraphBlock(secondPostId, "<body-2>")), 240)).thenReturn("<body-2>");

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult page = service.listGlobalHotFeed(null, "", 2);

        verify(postSummaryCache).putVersioned(argThat((List<PostSummaryCache.VersionedSummary> items) ->
                items.stream().anyMatch(it -> secondPostId.equals(it.summary().id())
                        && it.aggregateVersion() == 7L
                        && it.scoreVersion() == 3L)));
        assertThat(page.items()).extracting(PostSummaryResult::id).containsExactly(firstPostId, secondPostId);
        assertThat(page.items().get(0).title()).isEqualTo("<cached>");
        assertThat(page.items().get(1).title()).isEqualTo("<second>");
    }

    @Test
    void listGlobalHotFeedShouldReturnCacheRankVersion() {
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);

        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");
        when(postFeedCache.readGlobalHotProjection("", 20)).thenReturn(cacheMiss());
        when(postContentRepository.listPosts(0, 20, 21, PostContentRepository.ORDER_HOT, null, null))
                .thenReturn(List.of());

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec
        );

        FeedPageResult page = service.listGlobalHotFeed(null, "", 20);

        assertThat(page.rankVersion()).isEqualTo("hot-v2");
    }

    @Test
    void feedPageResultShouldNormalizeNullableFields() {
        FeedPageResult page = new FeedPageResult(null, null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isEmpty();
        assertThat(page.rankVersion()).isEmpty();

        FeedPageResult withNullItem = new FeedPageResult(
                java.util.Arrays.asList((PostSummaryResult) null),
                "cursor",
                "rank"
        );
        assertThat(withNullItem.items()).containsExactly((PostSummaryResult) null);
        assertThatCode(() -> withNullItem.items().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static FeedReadApplicationService service(
            PostFeedCache postFeedCache,
            PostContentRepository postContentRepository,
            CommentContentRepository commentContentRepository,
            TagContentRepository tagContentRepository,
            PostContentBlockRepository postContentBlockRepository,
            PostSummaryCache postSummaryCache,
            PostContentBlockTextProjector postContentBlockTextProjector,
            PostSummaryAssembler postSummaryAssembler,
            FeedCursorCodec feedCursorCodec
    ) {
        return service(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec,
                new ContentFeedPolicyProperties()
        );
    }

    private static FeedReadApplicationService service(
            PostFeedCache postFeedCache,
            PostContentRepository postContentRepository,
            CommentContentRepository commentContentRepository,
            TagContentRepository tagContentRepository,
            PostContentBlockRepository postContentBlockRepository,
            PostSummaryCache postSummaryCache,
            PostContentBlockTextProjector postContentBlockTextProjector,
            PostSummaryAssembler postSummaryAssembler,
            FeedCursorCodec feedCursorCodec,
            ContentFeedPolicyProperties policyProperties
    ) {
        return new FeedReadApplicationService(
                postFeedCache,
                postContentRepository,
                new PostFeedSummaryLoader(
                        postContentRepository,
                        commentContentRepository,
                        tagContentRepository,
                        postContentBlockRepository,
                        postSummaryCache,
                        postContentBlockTextProjector,
                        postSummaryAssembler
                ),
                feedCursorCodec,
                policyProperties,
                new HotFeedReadMetrics((io.micrometer.core.instrument.MeterRegistry) null),
                new ContentHotPathProperties(),
                loaderSingleFlight()
        );
    }

    private static HotPathSingleFlight loaderSingleFlight() {
        return new HotPathSingleFlight() {
            @Override
            public <T> T execute(
                    String scope,
                    String key,
                    java.time.Duration ttl,
                    java.util.function.Supplier<T> loader,
                    java.util.function.Supplier<T> fallbackWhenBusy
            ) {
                return loader.get();
            }
        };
    }

    private static DiscussPost post(UUID postId, String title) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(uuid(100));
        post.setTitle(title);
        post.setCreateTime(new Date(1_000));
        post.setAggregateVersion(7L);
        post.setScoreVersion(3L);
        return post;
    }

    private static PostFeedCache.HotProjectionEntry projectionOf(DiscussPost post) {
        return new PostFeedCache.HotProjectionEntry(
                post.getId(), post.getType(), post.getScore(), post.getCreateTime());
    }

    private static PostFeedCache.HotProjectionPage cacheMiss() {
        return new PostFeedCache.HotProjectionPage(List.of(), 0L, false);
    }

    private static Comment lastActivity(UUID userId, String content) {
        return aComment()
                .userId(userId)
                .content(content)
                .createTime(new Date(2_000))
                .build();
    }

    private static PostSummaryResult summary(UUID postId, String title) {
        return summary(postId, title, uuid(200));
    }

    private static PostSummaryResult summary(UUID postId, String title, UUID categoryId) {
        return new PostSummaryResult(
                postId,
                uuid(100),
                title,
                "<preview>",
                0,
                0,
                new Date(1_000),
                0,
                0.0,
                categoryId,
                List.of(),
                null,
                null,
                null,
                ""
        );
    }

    private static PostSummaryResult summaryWithRank(
            UUID postId,
            String title,
            int type,
            double score,
            Date createTime
    ) {
        return new PostSummaryResult(
                postId,
                uuid(100),
                title,
                "<preview>",
                type,
                0,
                createTime,
                0,
                score,
                uuid(200),
                List.of(),
                null,
                null,
                null,
                ""
        );
    }

    private static void mockSummaryDependencies(
            CommentContentRepository commentContentRepository,
            TagContentRepository tagContentRepository,
            PostContentBlockRepository postContentBlockRepository,
            PostContentBlockTextProjector postContentBlockTextProjector,
            List<DiscussPost> posts
    ) {
        Map<UUID, List<String>> tagsByPostId = posts.stream()
                .collect(java.util.stream.Collectors.toMap(DiscussPost::getId, post -> List.of("tag-" + post.getId())));
        Map<UUID, List<PostContentBlock>> blocksByPostId = posts.stream()
                .collect(java.util.stream.Collectors.toMap(DiscussPost::getId, post -> List.of(paragraphBlock(post.getId(), post.getTitle() + "-body"))));

        lenient().when(commentContentRepository.getLatestPostActivitiesByPostIds(org.mockito.ArgumentMatchers.anyList())).thenReturn(Map.of());
        lenient().when(tagContentRepository.getTagsByPostIds(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> {
                    List<UUID> postIds = invocation.getArgument(0);
                    return postIds.stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> tagsByPostId.getOrDefault(id, List.of())));
                });
        lenient().when(postContentBlockRepository.listByPostIds(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> {
                    List<UUID> postIds = invocation.getArgument(0);
                    return postIds.stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> blocksByPostId.getOrDefault(id, List.of())));
                });
        lenient().when(postContentBlockTextProjector.preview(org.mockito.ArgumentMatchers.anyList(), eq(240)))
                .thenAnswer(invocation -> {
                    List<PostContentBlock> blocks = invocation.getArgument(0);
                    if (blocks == null || blocks.isEmpty()) {
                        return "";
                    }
                    return blocks.get(0).text();
                });
    }

    private static PostContentBlock paragraphBlock(UUID postId, String text) {
        return new PostContentBlock(
                uuid(500),
                postId,
                0,
                "paragraph",
                text,
                null,
                null,
                null,
                null,
                null
        );
    }
}
