package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.FeedPageResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedReadApplicationServiceReliabilityTest {

    @Test
    void hotFeedReadMetricsShouldUseBoundedCacheTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HotFeedReadMetrics metrics = new HotFeedReadMetrics(registry);

        metrics.record("hit", "global");
        metrics.record("fallback", "board");
        metrics.record("degraded", "global");

        assertThat(registry.counter(
                "community_cache_requests_total",
                "cache", "hot_feed",
                "result", "hit",
                "scope", "global"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "community_cache_requests_total",
                "cache", "hot_feed",
                "result", "fallback",
                "scope", "board"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "community_cache_requests_total",
                "cache", "hot_feed",
                "result", "degraded",
                "scope", "global"
        ).count()).isEqualTo(1.0);
    }

    @Test
    void listGlobalHotFeedShouldRecordHitOnceForSuccessfulCachedRead() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec();
        UUID postId = uuid(1);

        when(postFeedCache.readGlobalHotIds("", 2)).thenReturn(List.of(postId));
        when(postFeedCache.readGlobalHotIds(feedCursorCodec.encodePage(1, 2), 2)).thenReturn(List.of());
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");
        when(postFeedSummaryLoader.readSummaries(List.of(postId)))
                .thenReturn(List.of(summary(postId, "<cached>")));

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                registry,
                new ContentFeedPolicyProperties()
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).extracting(PostSummaryResult::id).containsExactly(postId);
        assertThat(countMetric(registry, "hit", "global")).isEqualTo(1.0);
        assertThat(totalMetricCount(registry)).isEqualTo(1.0);
    }

    @Test
    void listGlobalHotFeedShouldRecordFallbackWhenFeedCacheIsCold() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
        DiscussPost fallbackPost = post(uuid(11), "<fallback>");
        fallbackPost.setScore(91.0);

        when(postFeedCache.readGlobalHotIds("", 2)).thenReturn(List.of());
        when(postContentRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of(fallbackPost));
        when(postContentRepository.listPosts(1, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of());
        when(postFeedSummaryLoader.assembleSummaries(List.of(fallbackPost)))
                .thenReturn(List.of(summary(fallbackPost.getId(), fallbackPost.getTitle())));

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                registry,
                new ContentFeedPolicyProperties()
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).extracting(PostSummaryResult::id).containsExactly(fallbackPost.getId());
        assertThat(countMetric(registry, "fallback", "global")).isEqualTo(1.0);
        assertThat(totalMetricCount(registry)).isEqualTo(1.0);
    }

    @Test
    void listGlobalHotFeedShouldSkipRepositoryFallbackWhenSingleFlightIsBusy() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
        HotPathSingleFlight singleFlight = busySingleFlight();

        when(postFeedCache.readGlobalHotIds("", 20)).thenReturn(List.of());
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                registry,
                new ContentFeedPolicyProperties(),
                new ContentHotPathProperties(),
                singleFlight
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 20);

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(postContentRepository);
        assertThat(countMetric(registry, "singleflight_busy", "global")).isEqualTo(1.0);
    }

    @Test
    void listGlobalHotFeedShouldRecordFallbackWhenCachedSummaryHydrationFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
        DiscussPost fallbackPost = post(uuid(21), "<fallback>");
        fallbackPost.setScore(88.0);
        UUID cachedPostId = uuid(22);

        when(postFeedCache.readGlobalHotIds("", 2)).thenReturn(List.of(cachedPostId));
        when(postFeedSummaryLoader.readSummaries(List.of(cachedPostId))).thenThrow(new IllegalStateException("summary load failed"));
        when(postContentRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of(fallbackPost));
        when(postContentRepository.listPosts(1, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of());
        when(postFeedSummaryLoader.assembleSummaries(List.of(fallbackPost)))
                .thenReturn(List.of(summary(fallbackPost.getId(), fallbackPost.getTitle())));

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                registry,
                new ContentFeedPolicyProperties()
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).extracting(PostSummaryResult::id).containsExactly(fallbackPost.getId());
        assertThat(countMetric(registry, "fallback", "global")).isEqualTo(1.0);
        assertThat(countMetric(registry, "degraded", "global")).isZero();
        assertThat(totalMetricCount(registry)).isEqualTo(1.0);
    }

    @Test
    void listGlobalHotFeedShouldRecordEmptyWhenFallbackDisabledAndCacheMisses() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
        ContentFeedPolicyProperties policyProperties = new ContentFeedPolicyProperties();
        policyProperties.setLatestFallbackEnabled(false);

        when(postFeedCache.readGlobalHotIds("", 2)).thenReturn(List.of());
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                registry,
                policyProperties
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).isEmpty();
        assertThat(result.rankVersion()).isEqualTo("hot-v2");
        assertThat(countMetric(registry, "empty", "global")).isEqualTo(1.0);
        assertThat(totalMetricCount(registry)).isEqualTo(1.0);
    }

    @Test
    void listGlobalHotFeedShouldRecordDegradedWhenFeedCacheReadFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
        ContentFeedPolicyProperties policyProperties = new ContentFeedPolicyProperties();
        policyProperties.setLatestFallbackEnabled(false);
        policyProperties.setHotRankVersion("hot-v9");

        when(postFeedCache.readGlobalHotIds("", 2)).thenThrow(new IllegalStateException("redis down"));
        when(postFeedCache.readRankVersion()).thenReturn("hot-v2");

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                registry,
                policyProperties
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).isEmpty();
        assertThat(result.rankVersion()).isEqualTo("hot-v2");
        assertThat(countMetric(registry, "degraded", "global")).isEqualTo(1.0);
        assertThat(totalMetricCount(registry)).isEqualTo(1.0);
    }

    @Test
    void listGlobalHotFeedShouldRecordDegradedWhenFeedCacheReadFallsBackToRepository() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
        DiscussPost fallbackPost = post(uuid(25), "<fallback>");
        fallbackPost.setScore(77.0);

        when(postFeedCache.readGlobalHotIds("", 2)).thenThrow(new IllegalStateException("redis down"));
        when(postContentRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of(fallbackPost));
        when(postContentRepository.listPosts(1, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of());
        when(postFeedSummaryLoader.assembleSummaries(List.of(fallbackPost)))
                .thenReturn(List.of(summary(fallbackPost.getId(), fallbackPost.getTitle())));

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                registry,
                new ContentFeedPolicyProperties()
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).extracting(PostSummaryResult::id).containsExactly(fallbackPost.getId());
        assertThat(countMetric(registry, "degraded", "global")).isEqualTo(1.0);
        assertThat(countMetric(registry, "fallback", "global")).isZero();
        assertThat(totalMetricCount(registry)).isEqualTo(1.0);
    }

    @Test
    void listGlobalHotFeedShouldReturnFallbackWhenFeedCacheWarmupFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
        ContentFeedPolicyProperties policyProperties = new ContentFeedPolicyProperties();
        policyProperties.setHotRankVersion("hot-v2");
        DiscussPost fallbackPost = post(uuid(26), "<fallback>");
        fallbackPost.setScore(79.0);

        when(postFeedCache.readGlobalHotIds("", 2)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("feed cache warmup failed"))
                .when(postFeedCache).writeRankVersion("hot-v2");
        when(postContentRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of(fallbackPost));
        when(postContentRepository.listPosts(1, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of());
        when(postFeedSummaryLoader.assembleSummaries(List.of(fallbackPost)))
                .thenReturn(List.of(summary(fallbackPost.getId(), fallbackPost.getTitle())));

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                registry,
                policyProperties
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).extracting(PostSummaryResult::id).containsExactly(fallbackPost.getId());
        assertThat(result.rankVersion()).isEqualTo("hot-v2");
        assertThat(countMetric(registry, "fallback", "global")).isEqualTo(1.0);
        assertThat(totalMetricCount(registry)).isEqualTo(1.0);
    }

    @Test
    void listGlobalHotFeedShouldReturnFallbackWhenSummaryCacheBackfillFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
        ContentFeedPolicyProperties policyProperties = new ContentFeedPolicyProperties();
        policyProperties.setHotRankVersion("hot-v2");
        DiscussPost fallbackPost = post(uuid(27), "<fallback>");
        fallbackPost.setScore(80.0);
        List<PostSummaryResult> summaries = List.of(summary(fallbackPost.getId(), fallbackPost.getTitle()));

        when(postFeedCache.readGlobalHotIds("", 2)).thenReturn(List.of());
        when(postContentRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of(fallbackPost));
        when(postContentRepository.listPosts(1, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of());
        when(postFeedSummaryLoader.assembleSummaries(List.of(fallbackPost))).thenReturn(summaries);
        org.mockito.Mockito.doThrow(new IllegalStateException("summary cache backfill failed"))
                .when(postSummaryCache).putAll(summaries);

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                registry,
                policyProperties
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).extracting(PostSummaryResult::id).containsExactly(fallbackPost.getId());
        assertThat(result.rankVersion()).isEqualTo("hot-v2");
        assertThat(countMetric(registry, "fallback", "global")).isEqualTo(1.0);
        assertThat(totalMetricCount(registry)).isEqualTo(1.0);
    }

    @Test
    void listGlobalHotFeedShouldRecordDegradedOnceWhenRankVersionFallsBack() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
        FeedCursorCodec feedCursorCodec = new FeedCursorCodec();
        ContentFeedPolicyProperties policyProperties = new ContentFeedPolicyProperties();
        policyProperties.setHotRankVersion("hot-v9");
        UUID postId = uuid(31);

        when(postFeedCache.readGlobalHotIds("", 2)).thenReturn(List.of(postId));
        when(postFeedCache.readGlobalHotIds(feedCursorCodec.encodePage(1, 2), 2)).thenReturn(List.of());
        when(postFeedCache.readRankVersion()).thenThrow(new IllegalStateException("rank version unavailable"));
        when(postFeedSummaryLoader.readSummaries(List.of(postId)))
                .thenReturn(List.of(summary(postId, "<cached>")));

        FeedReadApplicationService service = service(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                registry,
                policyProperties
        );

        FeedPageResult result = service.listGlobalHotFeed(null, "", 2);

        assertThat(result.items()).extracting(PostSummaryResult::id).containsExactly(postId);
        assertThat(result.rankVersion()).isEqualTo("hot-v9");
        assertThat(countMetric(registry, "degraded", "global")).isEqualTo(1.0);
        assertThat(totalMetricCount(registry)).isEqualTo(1.0);
    }

    private static FeedReadApplicationService service(
            PostFeedCache postFeedCache,
            PostContentRepository postContentRepository,
            PostSummaryCache postSummaryCache,
            PostFeedSummaryLoader postFeedSummaryLoader,
            SimpleMeterRegistry registry,
            ContentFeedPolicyProperties policyProperties
    ) {
        return service(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                registry,
                policyProperties,
                new ContentHotPathProperties(),
                loaderSingleFlight()
        );
    }

    private static FeedReadApplicationService service(
            PostFeedCache postFeedCache,
            PostContentRepository postContentRepository,
            PostSummaryCache postSummaryCache,
            PostFeedSummaryLoader postFeedSummaryLoader,
            SimpleMeterRegistry registry,
            ContentFeedPolicyProperties policyProperties,
            ContentHotPathProperties hotPathProperties,
            HotPathSingleFlight hotPathSingleFlight
    ) {
        return new FeedReadApplicationService(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                new FeedCursorCodec(),
                policyProperties,
                new HotFeedReadMetrics(registry),
                hotPathProperties,
                hotPathSingleFlight
        );
    }

    private static double countMetric(SimpleMeterRegistry registry, String result, String scope) {
        Counter counter = registry.find("community_cache_requests_total")
                .tags("cache", "hot_feed", "result", result, "scope", scope)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static double totalMetricCount(SimpleMeterRegistry registry) {
        return registry.find("community_cache_requests_total")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private static HotPathSingleFlight loaderSingleFlight() {
        return new HotPathSingleFlight() {
            @Override
            public <T> T execute(String scope, String key, java.time.Duration ttl, java.util.function.Supplier<T> loader, java.util.function.Supplier<T> fallbackWhenBusy) {
                return loader.get();
            }
        };
    }

    private static HotPathSingleFlight busySingleFlight() {
        return new HotPathSingleFlight() {
            @Override
            public <T> T execute(String scope, String key, java.time.Duration ttl, java.util.function.Supplier<T> loader, java.util.function.Supplier<T> fallbackWhenBusy) {
                return fallbackWhenBusy.get();
            }
        };
    }

    private static DiscussPost post(UUID postId, String title) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(uuid(100));
        post.setTitle(title);
        post.setCreateTime(new Date(1_000));
        return post;
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
}
