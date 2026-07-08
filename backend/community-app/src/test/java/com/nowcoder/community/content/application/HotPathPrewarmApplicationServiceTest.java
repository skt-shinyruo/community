package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.HotPathPrewarmResult;
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
        verify(feedCache).upsertGlobalHot(globalPost.getId(), 100.0, "hot-v2");
        verify(feedCache).upsertBoardHot(boardId, boardPost.getId(), 90.0, "hot-v2");
        verify(summaryCache).putAll(List.of(globalSummary));
        verify(summaryCache).putAll(List.of(boardSummary));
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
        return post;
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
