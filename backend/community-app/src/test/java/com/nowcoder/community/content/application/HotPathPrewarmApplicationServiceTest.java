package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.HotPathPrewarmApplicationService.HotPathPrewarmResult;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotPathPrewarmApplicationServiceTest {

    @Test
    void prewarmShouldWarmGlobalBoardSummaryAndDetailHotKeys() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        CategoryContentRepository categoryRepository = mock(CategoryContentRepository.class);
        PostFeedCache feedCache = mock(PostFeedCache.class);
        PostSummaryCache summaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader summaryLoader = mock(PostFeedSummaryLoader.class);
        PostReadApplicationService postReadApplicationService = mock(PostReadApplicationService.class);
        ContentFeedPolicyProperties feedProperties = new ContentFeedPolicyProperties();
        ContentHotPathProperties hotPathProperties = new ContentHotPathProperties();
        hotPathProperties.getPrewarm().setPages(1);
        hotPathProperties.getPrewarm().setPageSize(2);
        hotPathProperties.getPrewarm().setBoardLimit(1);
        UUID boardId = uuid(9);
        DiscussPost globalPost = post(uuid(1), boardId, 100.0);
        DiscussPost boardPost = post(uuid(2), boardId, 90.0);
        PostSummaryResult globalSummary = summary(globalPost.getId());
        PostSummaryResult boardSummary = summary(boardPost.getId());

        when(postRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of(globalPost));
        when(categoryRepository.listCategories()).thenReturn(List.of(category(boardId)));
        when(postRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT, boardId, null)).thenReturn(List.of(boardPost));
        when(summaryLoader.assembleSummaries(List.of(globalPost))).thenReturn(List.of(globalSummary));
        when(summaryLoader.assembleSummaries(List.of(boardPost))).thenReturn(List.of(boardSummary));
        when(postReadApplicationService.getPostDetail(null, globalPost.getId())).thenReturn(detail(globalPost.getId()));
        when(postReadApplicationService.getPostDetail(null, boardPost.getId())).thenReturn(detail(boardPost.getId()));
        HotPathPrewarmApplicationService service = new HotPathPrewarmApplicationService(
                postRepository,
                categoryRepository,
                feedCache,
                summaryCache,
                summaryLoader,
                postReadApplicationService,
                feedProperties,
                hotPathProperties,
                loaderSingleFlight()
        );

        HotPathPrewarmResult result = service.prewarm();

        assertThat(result.feedPages()).isEqualTo(2);
        assertThat(result.summaries()).isEqualTo(2);
        assertThat(result.details()).isEqualTo(2);
        verify(feedCache).writeRankVersion("hot-v2");
        verify(feedCache).upsertGlobalHot(projectionOf(globalPost), "hot-v2", 7L, 3L);
        verify(feedCache).upsertBoardHot(boardId, projectionOf(boardPost), "hot-v2", 7L, 3L);
        verify(summaryLoader).cacheSummaries(List.of(globalPost), List.of(globalSummary));
        verify(summaryLoader).cacheSummaries(List.of(boardPost), List.of(boardSummary));
        verify(postReadApplicationService).getPostDetail(null, globalPost.getId());
        verify(postReadApplicationService).getPostDetail(null, boardPost.getId());
    }

    private static HotPathSingleFlight loaderSingleFlight() {
        return new HotPathSingleFlight() {
            @Override
            public <T> T execute(String scope, String key, java.time.Duration ttl, java.util.function.Supplier<T> loader, java.util.function.Supplier<T> fallbackWhenBusy) {
                return loader.get();
            }
        };
    }

    private static DiscussPost post(UUID postId, UUID boardId, double score) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(uuid(100));
        post.setCategoryId(boardId);
        post.setTitle("<title>");
        post.setScore(score);
        post.setCreateTime(new Date(1_000));
        post.setAggregateVersion(7L);
        post.setScoreVersion(3L);
        return post;
    }

    private static PostFeedCache.HotProjectionEntry projectionOf(DiscussPost post) {
        return new PostFeedCache.HotProjectionEntry(
                post.getId(), post.getType(), post.getScore(), post.getCreateTime());
    }

    private static Category category(UUID boardId) {
        Category category = new Category();
        category.setId(boardId);
        category.setName("board");
        return category;
    }

    private static PostSummaryResult summary(UUID postId) {
        return new PostSummaryResult(
                postId,
                uuid(100),
                "<title>",
                "<preview>",
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

    private static PostDetailResult detail(UUID postId) {
        return new PostDetailResult(
                postId,
                uuid(100),
                "<title>",
                List.of(),
                0,
                0,
                new Date(1_000),
                null,
                0,
                0,
                0.0,
                uuid(9),
                List.of(),
                0L,
                false,
                false
        );
    }
}
