package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostFeedSummaryLoaderTest {

    private PostContentRepository postRepository;
    private CommentContentRepository commentRepository;
    private TagContentRepository tagRepository;
    private PostContentBlockRepository blockRepository;
    private PostSummaryCache summaryCache;
    private PostContentBlockTextProjector textProjector;
    private PostSummaryAssembler assembler;
    private PostFeedSummaryLoader loader;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostContentRepository.class);
        commentRepository = mock(CommentContentRepository.class);
        tagRepository = mock(TagContentRepository.class);
        blockRepository = mock(PostContentBlockRepository.class);
        summaryCache = mock(PostSummaryCache.class);
        textProjector = mock(PostContentBlockTextProjector.class);
        assembler = mock(PostSummaryAssembler.class);
        loader = new PostFeedSummaryLoader(
                postRepository,
                commentRepository,
                tagRepository,
                blockRepository,
                summaryCache,
                textProjector,
                assembler
        );
    }

    @Test
    void serveCurrentPostsReturnsSummariesWhenBestEffortBackfillFails() {
        DiscussPost post = post(uuid(1), 7L, 3L);
        PostSummaryResult summary = summary(post.getId());
        stubAssembly(post, summary);
        doThrow(new IllegalStateException("cache unavailable")).when(summaryCache).putVersioned(anyList());

        assertThat(loader.serveCurrentPosts(List.of(post))).containsExactly(summary);
    }

    @Test
    void prewarmCurrentPostsPropagatesBackfillFailure() {
        DiscussPost post = post(uuid(2), 8L, 4L);
        stubAssembly(post, summary(post.getId()));
        doThrow(new IllegalStateException("cache unavailable")).when(summaryCache).putVersioned(anyList());

        assertThatThrownBy(() -> loader.prewarmCurrentPosts(List.of(post)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cache unavailable");
    }

    @Test
    void prewarmCurrentPostsWritesAggregateAndScoreVersions() {
        DiscussPost post = post(uuid(3), 9L, 5L);
        PostSummaryResult summary = summary(post.getId());
        stubAssembly(post, summary);

        assertThat(loader.prewarmCurrentPosts(List.of(post))).containsExactly(summary);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PostSummaryCache.VersionedSummary>> entries = ArgumentCaptor.forClass(List.class);
        verify(summaryCache).putVersioned(entries.capture());
        assertThat(entries.getValue()).singleElement().satisfies(entry -> {
            assertThat(entry.summary()).isSameAs(summary);
            assertThat(entry.aggregateVersion()).isEqualTo(9L);
            assertThat(entry.scoreVersion()).isEqualTo(5L);
        });
    }

    @Test
    void readSummariesPreservesRequestedOrderAcrossCacheHitsAndMisses() {
        UUID loadedId = uuid(4);
        UUID cachedId = uuid(5);
        DiscussPost loadedPost = post(loadedId, 10L, 6L);
        PostSummaryResult loaded = summary(loadedId);
        PostSummaryResult cached = summary(cachedId);
        when(summaryCache.getAll(List.of(cachedId, loadedId, cachedId))).thenReturn(Map.of(cachedId, cached));
        when(postRepository.listPostsByIds(List.of(loadedId))).thenReturn(List.of(loadedPost));
        stubAssembly(loadedPost, loaded);

        assertThat(loader.readSummaries(List.of(cachedId, loadedId, cachedId)))
                .containsExactly(cached, loaded, cached);
    }

    private void stubAssembly(DiscussPost post, PostSummaryResult summary) {
        List<UUID> postIds = List.of(post.getId());
        Comment activity = mock(Comment.class);
        List<String> tags = List.of("java");
        List<PostContentBlock> blocks = List.of();
        when(commentRepository.getLatestPostActivitiesByPostIds(postIds)).thenReturn(Map.of(post.getId(), activity));
        when(tagRepository.getTagsByPostIds(postIds)).thenReturn(Map.of(post.getId(), tags));
        when(blockRepository.listByPostIds(postIds)).thenReturn(Map.of(post.getId(), blocks));
        when(textProjector.preview(blocks, 240)).thenReturn("preview");
        when(assembler.assemble(post, activity, tags, "preview")).thenReturn(summary);
    }

    private static DiscussPost post(UUID postId, long aggregateVersion, long scoreVersion) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setAggregateVersion(aggregateVersion);
        post.setScoreVersion(scoreVersion);
        return post;
    }

    private static PostSummaryResult summary(UUID postId) {
        return new PostSummaryResult(
                postId,
                uuid(100),
                "title",
                "preview",
                0,
                0,
                new Date(1_000),
                0,
                0.0,
                uuid(9),
                List.of(),
                null,
                null,
                null,
                ""
        );
    }
}
