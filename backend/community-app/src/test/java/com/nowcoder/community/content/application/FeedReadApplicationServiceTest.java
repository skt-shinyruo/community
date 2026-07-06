package com.nowcoder.community.content.application;

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
import static org.mockito.Mockito.when;

class FeedReadApplicationServiceTest {

    @Test
    void listGlobalHotFeedShouldReadOneExtraRowAndEmitNextCursor() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec();
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        UUID firstPostId = uuid(1);
        UUID secondPostId = uuid(2);
        UUID extraPostId = uuid(3);

        when(postContentRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT, null, null))
                .thenReturn(List.of(post(firstPostId, "<first>"), post(secondPostId, "<second>")));
        when(postContentRepository.listPosts(1, 2, PostContentRepository.ORDER_HOT, null, null))
                .thenReturn(List.of(post(extraPostId, "<extra>")));
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

        FeedReadApplicationService service = new FeedReadApplicationService(
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

        assertThat(result.rankVersion()).isEqualTo("db-fallback-v1");
        assertThat(next.page()).isEqualTo(1);
        assertThat(next.size()).isEqualTo(2);
        assertThat(result.items()).extracting(PostSummaryResult::id).containsExactly(firstPostId, secondPostId);
        assertThat(result.items().get(0).title()).isEqualTo("<first>");
        assertThat(result.items().get(0).lastReplyPreview()).isEqualTo("<reply>");
    }

    @Test
    void listBoardHotFeedShouldUseBoardIdAsCategoryFilter() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec();
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        UUID boardId = uuid(8);
        UUID postId = uuid(21);

        when(postContentRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT, boardId, null))
                .thenReturn(List.of(post(postId, "<board-post>")));
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(postId))).thenReturn(Map.of());
        when(tagContentRepository.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("board")));
        when(postContentBlockRepository.listByPostIds(List.of(postId)))
                .thenReturn(Map.of(postId, List.of(paragraphBlock(postId, "<board-preview>"))));
        when(postContentBlockTextProjector.preview(List.of(paragraphBlock(postId, "<board-preview>")), 240)).thenReturn("<board-preview>");

        FeedReadApplicationService service = new FeedReadApplicationService(
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
    void listGlobalHotFeedShouldContinueFromNextCursorWithoutSkippingRows() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec();
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        List<DiscussPost> allPosts = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> post(uuid(i), "<post-" + i + ">"))
                .toList();

        mockRepositoryPagination(postContentRepository, allPosts, null);
        mockSummaryDependencies(commentContentRepository, tagContentRepository, postContentBlockRepository, postContentBlockTextProjector, allPosts);

        FeedReadApplicationService service = new FeedReadApplicationService(
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
        FeedPageResult secondPage = service.listGlobalHotFeed(null, firstPage.nextCursor(), 2);

        assertThat(firstPage.items()).extracting(PostSummaryResult::title).containsExactly("<post-1>", "<post-2>");
        assertThat(secondPage.items()).extracting(PostSummaryResult::title).containsExactly("<post-3>", "<post-4>");
    }

    @Test
    void listGlobalHotFeedShouldTreatInvalidCursorAsFirstPage() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec();
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        List<DiscussPost> allPosts = List.of(post(uuid(1), "<first>"), post(uuid(2), "<second>"));

        mockRepositoryPagination(postContentRepository, allPosts, null);
        mockSummaryDependencies(commentContentRepository, tagContentRepository, postContentBlockRepository, postContentBlockTextProjector, allPosts);

        FeedReadApplicationService service = new FeedReadApplicationService(
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
    void globalFeedShouldBackfillMissingSummariesIntoCache() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec();
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        UUID firstPostId = uuid(1);
        UUID secondPostId = uuid(2);
        DiscussPost firstPost = post(firstPostId, "<first>");
        DiscussPost secondPost = post(secondPostId, "<second>");

        when(postContentRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT, null, null))
                .thenReturn(List.of(firstPost, secondPost));
        when(postContentRepository.listPosts(1, 2, PostContentRepository.ORDER_HOT, null, null))
                .thenReturn(List.of());
        when(postSummaryCache.getAll(List.of(firstPostId, secondPostId)))
                .thenReturn(Map.of(firstPostId, summary(firstPostId, "<cached>")));
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(secondPostId))).thenReturn(Map.of());
        when(tagContentRepository.getTagsByPostIds(List.of(secondPostId))).thenReturn(Map.of(secondPostId, List.of("spring")));
        when(postContentBlockRepository.listByPostIds(List.of(secondPostId)))
                .thenReturn(Map.of(secondPostId, List.of(paragraphBlock(secondPostId, "<body-2>"))));
        when(postContentBlockTextProjector.preview(List.of(paragraphBlock(secondPostId, "<body-2>")), 240)).thenReturn("<body-2>");

        FeedReadApplicationService service = new FeedReadApplicationService(
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

        verify(postSummaryCache).putAll(argThat((List<PostSummaryResult> items) ->
                items.stream().anyMatch(it -> secondPostId.equals(it.id()))));
        assertThat(page.items()).extracting(PostSummaryResult::id).containsExactly(firstPostId, secondPostId);
        assertThat(page.items().get(0).title()).isEqualTo("<cached>");
        assertThat(page.items().get(1).title()).isEqualTo("<second>");
    }

    private static DiscussPost post(UUID postId, String title) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(uuid(100));
        post.setTitle(title);
        post.setCreateTime(new Date(1_000));
        return post;
    }

    private static Comment lastActivity(UUID userId, String content) {
        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setCreateTime(new Date(2_000));
        return comment;
    }

    private static PostSummaryResult summary(UUID postId, String title) {
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
                uuid(200),
                List.of(),
                null,
                null,
                null,
                ""
        );
    }

    private static void mockRepositoryPagination(
            PostContentRepository postContentRepository,
            List<DiscussPost> posts,
            UUID boardId
    ) {
        lenient().when(postContentRepository.listPosts(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), eq(PostContentRepository.ORDER_HOT), eq(boardId), isNull()))
                .thenAnswer(invocation -> {
                    int page = invocation.getArgument(0);
                    int size = invocation.getArgument(1);
                    int offset = Math.max(0, page) * Math.max(1, size);
                    if (offset >= posts.size()) {
                        return List.of();
                    }
                    int end = Math.min(posts.size(), offset + Math.max(1, size));
                    return posts.subList(offset, end);
                });
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
