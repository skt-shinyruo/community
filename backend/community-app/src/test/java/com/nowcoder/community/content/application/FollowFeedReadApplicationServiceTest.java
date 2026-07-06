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
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FollowFeedReadApplicationServiceTest {

    @Test
    void listFollowFeedShouldMergeFolloweeRecentPostsAndCachePage() {
        SocialFollowQueryApi followQueryApi = mock(SocialFollowQueryApi.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        FollowFeedCache followFeedCache = mock(FollowFeedCache.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        FollowFeedCursorCodec followFeedCursorCodec = new FollowFeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        FollowFeedReadApplicationService service = new FollowFeedReadApplicationService(
                followQueryApi,
                postContentRepository,
                followFeedCache,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                followFeedCursorCodec
        );

        UUID viewerId = UUID.randomUUID();
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        DiscussPost post1 = post(authorA, Instant.parse("2026-07-06T10:00:00Z"));
        DiscussPost post2 = post(authorB, Instant.parse("2026-07-06T09:00:00Z"));

        when(followQueryApi.listFolloweeIds(viewerId, 200)).thenReturn(List.of(authorA, authorB));
        when(postContentRepository.listRecentVisiblePostsByAuthorIds(List.of(authorA, authorB), 21))
                .thenReturn(List.of(post1, post2));
        when(followFeedCache.getOrLoadPage(eq(viewerId), eq(""), eq(20), any()))
                .thenAnswer(invocation -> invocation.<Supplier<FollowFeedCache.FollowFeedPageSlice>>getArgument(3).get());
        when(postSummaryCache.getAll(List.of(post1.getId(), post2.getId()))).thenReturn(Map.of());
        when(postContentRepository.listPostsByIds(List.of(post1.getId(), post2.getId())))
                .thenReturn(List.of(post1, post2));
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(post1.getId(), post2.getId()))).thenReturn(Map.of());
        when(tagContentRepository.getTagsByPostIds(List.of(post1.getId(), post2.getId()))).thenReturn(Map.of());
        when(postContentBlockRepository.listByPostIds(List.of(post1.getId(), post2.getId()))).thenReturn(Map.of());

        FeedPageResult page = service.listFollowFeed(viewerId, "", 20);

        assertThat(page.items()).extracting(PostSummaryResult::id).containsExactly(post1.getId(), post2.getId());
        assertThat(page.rankVersion()).isEqualTo("follow-v1");
        verify(postContentRepository).listRecentVisiblePostsByAuthorIds(List.of(authorA, authorB), 21);
        verify(followFeedCache).getOrLoadPage(eq(viewerId), eq(""), eq(20), any());
    }

    @Test
    void listFollowFeedShouldUseEnrichedSummariesInsteadOfBareAssembly() {
        SocialFollowQueryApi followQueryApi = mock(SocialFollowQueryApi.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        FollowFeedCache followFeedCache = mock(FollowFeedCache.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        FollowFeedCursorCodec followFeedCursorCodec = new FollowFeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        FollowFeedReadApplicationService service = new FollowFeedReadApplicationService(
                followQueryApi,
                postContentRepository,
                followFeedCache,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                followFeedCursorCodec
        );

        UUID viewerId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        DiscussPost post = post(postId, authorId, Instant.parse("2026-07-06T10:00:00Z"));
        Comment lastActivity = lastActivity(UUID.randomUUID(), "<reply>");
        PostContentBlock block = paragraphBlock(postId, "<preview-body>");

        when(followFeedCache.getOrLoadPage(eq(viewerId), eq(""), eq(20), any()))
                .thenReturn(new FollowFeedCache.FollowFeedPageSlice(List.of(postId), null, null));
        when(postSummaryCache.getAll(List.of(postId))).thenReturn(Map.of());
        when(postContentRepository.listPostsByIds(List.of(postId))).thenReturn(List.of(post));
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(postId))).thenReturn(Map.of(postId, lastActivity));
        when(tagContentRepository.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("java", "spring")));
        when(postContentBlockRepository.listByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of(block)));
        when(postContentBlockTextProjector.preview(List.of(block), 240)).thenReturn("<preview-body>");

        FeedPageResult page = service.listFollowFeed(viewerId, "", 20);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.tags()).containsExactly("java", "spring");
            assertThat(item.preview()).isEqualTo("<preview-body>");
            assertThat(item.lastReplyPreview()).isEqualTo("<reply>");
        });
        verify(postSummaryCache).putAll(argThat((List<PostSummaryResult> items) ->
                items.stream().anyMatch(it -> postId.equals(it.id()) && it.tags().contains("java"))));
    }

    @Test
    void listFollowFeedShouldEmitNextCursorWhenIdPageHasExtraIdEvenIfRenderedItemsShrink() {
        SocialFollowQueryApi followQueryApi = mock(SocialFollowQueryApi.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        FollowFeedCache followFeedCache = mock(FollowFeedCache.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        FollowFeedCursorCodec followFeedCursorCodec = new FollowFeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        FollowFeedReadApplicationService service = new FollowFeedReadApplicationService(
                followQueryApi,
                postContentRepository,
                followFeedCache,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                followFeedCursorCodec
        );
        UUID viewerId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        UUID extraId = UUID.randomUUID();
        DiscussPost firstPost = post(firstId, UUID.randomUUID(), Instant.parse("2026-07-06T10:00:00Z"));

        when(followFeedCache.getOrLoadPage(eq(viewerId), eq(""), eq(2), any()))
                .thenReturn(new FollowFeedCache.FollowFeedPageSlice(
                        List.of(firstId, missingId, extraId),
                        firstPost.getCreateTime(),
                        missingId
                ));
        when(postSummaryCache.getAll(List.of(firstId, missingId))).thenReturn(Map.of());
        when(postContentRepository.listPostsByIds(List.of(firstId, missingId)))
                .thenReturn(List.of(firstPost));
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(firstId, missingId))).thenReturn(Map.of());
        when(tagContentRepository.getTagsByPostIds(List.of(firstId, missingId))).thenReturn(Map.of());
        when(postContentBlockRepository.listByPostIds(List.of(firstId, missingId))).thenReturn(Map.of());

        FeedPageResult page = service.listFollowFeed(viewerId, "", 2);

        assertThat(page.items()).extracting(PostSummaryResult::id).containsExactly(firstId);
        assertThat(page.nextCursor()).isNotBlank();
        assertThat(decodeCursorJson(page.nextCursor()))
                .contains("\"size\":2")
                .contains("\"anchorPostId\":\"" + missingId + "\"");
    }

    @Test
    void listFollowFeedShouldUseAnchorCursorForSubsequentPageQuery() {
        SocialFollowQueryApi followQueryApi = mock(SocialFollowQueryApi.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        FollowFeedCache followFeedCache = mock(FollowFeedCache.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        FollowFeedCursorCodec followFeedCursorCodec = new FollowFeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        FollowFeedReadApplicationService service = new FollowFeedReadApplicationService(
                followQueryApi,
                postContentRepository,
                followFeedCache,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                followFeedCursorCodec
        );
        UUID viewerId = UUID.randomUUID();
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        DiscussPost first = post(UUID.randomUUID(), authorA, Instant.parse("2026-07-06T12:00:00Z"));
        DiscussPost second = post(UUID.randomUUID(), authorB, Instant.parse("2026-07-06T11:00:00Z"));
        DiscussPost probe = post(UUID.randomUUID(), authorA, Instant.parse("2026-07-06T10:00:00Z"));
        DiscussPost anchored = post(UUID.randomUUID(), authorB, Instant.parse("2026-07-06T09:00:00Z"));

        when(followQueryApi.listFolloweeIds(viewerId, 200)).thenReturn(List.of(authorA, authorB));
        when(followFeedCache.getOrLoadPage(eq(viewerId), eq(""), eq(2), any()))
                .thenAnswer(invocation -> invocation.<Supplier<FollowFeedCache.FollowFeedPageSlice>>getArgument(3).get());
        when(postContentRepository.listRecentVisiblePostsByAuthorIds(List.of(authorA, authorB), 3))
                .thenReturn(List.of(first, second, probe));
        when(postSummaryCache.getAll(List.of(first.getId(), second.getId()))).thenReturn(Map.of());
        when(postSummaryCache.getAll(List.of(anchored.getId()))).thenReturn(Map.of());
        when(postContentRepository.listPostsByIds(List.of(first.getId(), second.getId()))).thenReturn(List.of(first, second));
        when(postContentRepository.listPostsByIds(List.of(anchored.getId()))).thenReturn(List.of(anchored));
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(first.getId(), second.getId()))).thenReturn(Map.of());
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(anchored.getId()))).thenReturn(Map.of());
        when(tagContentRepository.getTagsByPostIds(List.of(first.getId(), second.getId()))).thenReturn(Map.of());
        when(tagContentRepository.getTagsByPostIds(List.of(anchored.getId()))).thenReturn(Map.of());
        when(postContentBlockRepository.listByPostIds(List.of(first.getId(), second.getId()))).thenReturn(Map.of());
        when(postContentBlockRepository.listByPostIds(List.of(anchored.getId()))).thenReturn(Map.of());

        FeedPageResult firstPage = service.listFollowFeed(viewerId, "", 2);
        when(followFeedCache.getOrLoadPage(eq(viewerId), eq(firstPage.nextCursor()), eq(2), any()))
                .thenAnswer(invocation -> invocation.<Supplier<FollowFeedCache.FollowFeedPageSlice>>getArgument(3).get());
        when(postContentRepository.listRecentVisiblePostsByAuthorIdsBefore(
                eq(List.of(authorA, authorB)),
                eq(second.getCreateTime()),
                eq(second.getId()),
                eq(3)
        )).thenReturn(List.of(anchored));

        FeedPageResult secondPage = service.listFollowFeed(viewerId, firstPage.nextCursor(), 2);

        assertThat(secondPage.items()).extracting(PostSummaryResult::id).containsExactly(anchored.getId());
        verify(postContentRepository).listRecentVisiblePostsByAuthorIds(List.of(authorA, authorB), 3);
        verify(postContentRepository).listRecentVisiblePostsByAuthorIdsBefore(
                List.of(authorA, authorB),
                second.getCreateTime(),
                second.getId(),
                3
        );
    }

    @Test
    void listFollowFeedShouldPreserveRepositoryOrderForEqualTimestampAnchor() {
        SocialFollowQueryApi followQueryApi = mock(SocialFollowQueryApi.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        FollowFeedCache followFeedCache = mock(FollowFeedCache.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository postContentBlockRepository = mock(PostContentBlockRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostContentBlockTextProjector postContentBlockTextProjector = mock(PostContentBlockTextProjector.class);
        ContentTextCodec contentTextCodec = mock(ContentTextCodec.class);
        when(contentTextCodec.decodeOnRead(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(contentTextCodec);
        FollowFeedCursorCodec followFeedCursorCodec = new FollowFeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        FollowFeedReadApplicationService service = new FollowFeedReadApplicationService(
                followQueryApi,
                postContentRepository,
                followFeedCache,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                followFeedCursorCodec
        );
        UUID viewerId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant sameTime = Instant.parse("2026-07-06T12:00:00Z");
        DiscussPost first = post(UUID.fromString("00000000-0000-0000-0000-000000000003"), authorId, sameTime);
        DiscussPost second = post(UUID.fromString("00000000-0000-0000-0000-000000000001"), authorId, sameTime);
        DiscussPost probe = post(UUID.fromString("00000000-0000-0000-0000-000000000002"), authorId, sameTime);

        when(followQueryApi.listFolloweeIds(viewerId, 200)).thenReturn(List.of(authorId));
        when(followFeedCache.getOrLoadPage(eq(viewerId), eq(""), eq(2), any()))
                .thenAnswer(invocation -> invocation.<Supplier<FollowFeedCache.FollowFeedPageSlice>>getArgument(3).get());
        when(postContentRepository.listRecentVisiblePostsByAuthorIds(List.of(authorId), 3))
                .thenReturn(List.of(first, second, probe));
        when(postSummaryCache.getAll(List.of(first.getId(), second.getId()))).thenReturn(Map.of());
        when(postContentRepository.listPostsByIds(List.of(first.getId(), second.getId()))).thenReturn(List.of(first, second));
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(first.getId(), second.getId()))).thenReturn(Map.of());
        when(tagContentRepository.getTagsByPostIds(List.of(first.getId(), second.getId()))).thenReturn(Map.of());
        when(postContentBlockRepository.listByPostIds(List.of(first.getId(), second.getId()))).thenReturn(Map.of());

        FeedPageResult page = service.listFollowFeed(viewerId, "", 2);

        assertThat(page.items()).extracting(PostSummaryResult::id).containsExactly(first.getId(), second.getId());
        assertThat(decodeCursorJson(page.nextCursor()))
                .contains("\"anchorPostId\":\"" + second.getId() + "\"");
    }

    private static DiscussPost post(UUID postId, UUID authorId, Instant createTime) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(authorId);
        post.setTitle("post-" + authorId);
        post.setCreateTime(Date.from(createTime));
        return post;
    }

    private static DiscussPost post(UUID authorId, Instant createTime) {
        return post(UUID.randomUUID(), authorId, createTime);
    }

    private static Comment lastActivity(UUID userId, String content) {
        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setCreateTime(new Date(2_000));
        return comment;
    }

    private static PostContentBlock paragraphBlock(UUID postId, String text) {
        return new PostContentBlock(
                UUID.randomUUID(),
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

    private static String decodeCursorJson(String cursor) {
        return new String(Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
    }
}
