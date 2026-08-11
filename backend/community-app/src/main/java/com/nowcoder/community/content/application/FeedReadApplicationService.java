package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.FeedPageResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

@Service
public class FeedReadApplicationService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final PostFeedCache postFeedCache;
    private final PostContentRepository postContentRepository;
    private final PostFeedSummaryLoader postFeedSummaryLoader;
    private final FeedCursorCodec feedCursorCodec;
    private final ContentFeedPolicyProperties policyProperties;
    private final HotFeedReadMetrics hotFeedReadMetrics;
    private final ContentHotPathProperties hotPathProperties;
    private final HotPathSingleFlight hotPathSingleFlight;

    public FeedReadApplicationService(
            PostFeedCache postFeedCache,
            PostContentRepository postContentRepository,
            PostFeedSummaryLoader postFeedSummaryLoader,
            FeedCursorCodec feedCursorCodec,
            ContentFeedPolicyProperties policyProperties,
            HotFeedReadMetrics hotFeedReadMetrics,
            ContentHotPathProperties hotPathProperties,
            HotPathSingleFlight hotPathSingleFlight
    ) {
        this.postFeedCache = requireNonNull(postFeedCache, "postFeedCache");
        this.postContentRepository = requireNonNull(postContentRepository, "postContentRepository");
        this.postFeedSummaryLoader = requireNonNull(postFeedSummaryLoader, "postFeedSummaryLoader");
        this.feedCursorCodec = requireNonNull(feedCursorCodec, "feedCursorCodec");
        this.policyProperties = requireNonNull(policyProperties, "policyProperties");
        this.hotFeedReadMetrics = requireNonNull(hotFeedReadMetrics, "hotFeedReadMetrics");
        this.hotPathProperties = requireNonNull(hotPathProperties, "hotPathProperties");
        this.hotPathSingleFlight = requireNonNull(hotPathSingleFlight, "hotPathSingleFlight");
    }

    public FeedPageResult listGlobalHotFeed(UUID currentUserId, String cursor, int size) {
        return listHotFeed(cursor, size, null);
    }

    public FeedPageResult listBoardHotFeed(UUID currentUserId, UUID boardId, String cursor, int size) {
        return listHotFeed(cursor, size, boardId);
    }

    private FeedPageResult listHotFeed(String cursor, int size, UUID boardId) {
        FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
        int requestedLimit = state.size() > 0 ? normalizeRequestedSize(state.size()) : normalizeRequestedSize(size);
        LoadedFeedPage page = loadHotPage(cursor, state, requestedLimit, boardId);
        String nextCursor = page.hasNext() && state.page() < FeedCursorCodec.MAX_CURSOR_PAGE
                ? feedCursorCodec.encodeHotPage(
                        nextPage(state.page()),
                        requestedLimit,
                        page.nextBoundary(),
                        page.projectionEpoch()
                )
                : "";
        return new FeedPageResult(page.items(), nextCursor, page.rankVersion());
    }

    private List<PostSummaryResult> filterBoardItems(List<PostSummaryResult> items, UUID boardId) {
        if (boardId == null || items == null || items.isEmpty()) {
            return items == null ? List.of() : items;
        }
        return items.stream()
                .filter(item -> boardId.equals(item.categoryId()))
                .toList();
    }

    private PostFeedCache.HotProjectionPage readFeedProjection(String cursor, int size, UUID boardId) {
        return boardId == null
                ? postFeedCache.readGlobalHotProjection(cursor, size)
                : postFeedCache.readBoardHotProjection(boardId, cursor, size);
    }

    private LoadedFeedPage loadHotPage(String cursor, FeedCursorCodec.CursorState state, int limit, UUID boardId) {
        String scope = boardId == null ? "global" : "board";
        CachePageLoad cachePageLoad = loadCachePage(cursor, limit, boardId);
        if (cachePageLoad.page() != null) {
            hotFeedReadMetrics.record(cachePageLoad.result(), scope);
            return cachePageLoad.page();
        }
        return loadFallbackPage(state, limit, boardId, scope, cachePageLoad.cacheDegraded());
    }

    private CachePageLoad loadCachePage(
            String cursor,
            int limit,
            UUID boardId
    ) {
        PostFeedCache.HotProjectionPage projection;
        try {
            projection = readFeedProjection(cursor, limit, boardId);
        } catch (RuntimeException ex) {
            return CachePageLoad.degradedMiss();
        }
        return projection == null ? CachePageLoad.miss() : loadProjectedCachePage(projection, limit, boardId);
    }

    private CachePageLoad loadProjectedCachePage(
            PostFeedCache.HotProjectionPage projection,
            int limit,
            UUID boardId
    ) {
        if (projection.epoch() <= 0L || projection.entries().isEmpty() || !projection.hasNext()) {
            return CachePageLoad.miss();
        }
        List<PostFeedCache.HotProjectionEntry> entries = projection.entries().size() <= limit
                ? projection.entries()
                : projection.entries().subList(0, limit);
        List<UUID> ids = entries.stream().map(PostFeedCache.HotProjectionEntry::postId).toList();
        List<PostSummaryResult> items;
        try {
            items = postFeedSummaryLoader.readSummaries(ids);
        } catch (RuntimeException ex) {
            return CachePageLoad.miss();
        }
        if (!matchesCompleteProjection(entries, items, boardId)) {
            return CachePageLoad.miss();
        }
        RankVersionResult rankVersion = safeRankVersion();
        String result = rankVersion.degraded() ? "degraded" : "hit";
        PostFeedCache.HotProjectionEntry last = entries.get(entries.size() - 1);
        return CachePageLoad.page(
                new LoadedFeedPage(
                        filterBoardItems(items, boardId),
                        true,
                        rankVersion.value(),
                        new FeedCursorCodec.HotBoundary(
                                last.type(),
                                last.score(),
                                last.createTime(),
                                last.postId()
                        ),
                        projection.epoch()
                ),
                result
        );
    }

    private static boolean matchesCompleteProjection(
            List<PostFeedCache.HotProjectionEntry> entries,
            List<PostSummaryResult> items,
            UUID boardId
    ) {
        if (entries == null || items == null || entries.size() != items.size()) {
            return false;
        }
        for (int index = 0; index < entries.size(); index++) {
            PostFeedCache.HotProjectionEntry entry = entries.get(index);
            PostSummaryResult item = items.get(index);
            if (entry == null || item == null
                    || !entry.postId().equals(item.id())
                    || entry.type() != item.type()
                    || Double.compare(entry.score(), item.score()) != 0
                    || entry.createTime() == null
                    || item.createTime() == null
                    || entry.createTime().getTime() != item.createTime().getTime()
                    || (boardId != null && !boardId.equals(item.categoryId()))) {
                return false;
            }
        }
        return true;
    }

    private LoadedFeedPage loadFallbackPage(
            FeedCursorCodec.CursorState state,
            int limit,
            UUID boardId,
            String scope,
            boolean cacheDegraded
    ) {
        String singleFlightKey = boardId == null
                ? "global:" + state.page() + ":" + limit + ":" + boundaryKey(state)
                : "board:" + boardId + ":" + state.page() + ":" + limit + ":" + boundaryKey(state);
        return hotPathSingleFlight.execute(
                "feed",
                singleFlightKey,
                hotPathProperties.getSingleFlight().ttl(),
                () -> loadFallbackPageUnlocked(state, limit, boardId, scope, cacheDegraded),
                () -> {
                    RankVersionResult rankVersion = safeRankVersion();
                    hotFeedReadMetrics.record("singleflight_busy", scope);
                    return new LoadedFeedPage(List.of(), false, rankVersion.value(), null, 0L);
                }
        );
    }

    private LoadedFeedPage loadFallbackPageUnlocked(
            FeedCursorCodec.CursorState state,
            int limit,
            UUID boardId,
            String scope,
            boolean cacheDegraded
    ) {
        if (!policyProperties.isLatestFallbackEnabled()) {
            RankVersionResult rankVersion = safeRankVersion();
            hotFeedReadMetrics.record(cacheDegraded || rankVersion.degraded() ? "degraded" : "empty", scope);
            return new LoadedFeedPage(List.of(), false, rankVersion.value(), null, 0L);
        }
        List<DiscussPost> fetchedPosts = listFallbackPosts(state, limit, boardId);
        if (fetchedPosts.isEmpty()) {
            RankVersionResult rankVersion = safeRankVersion();
            hotFeedReadMetrics.record(cacheDegraded || rankVersion.degraded() ? "degraded" : "empty", scope);
            return new LoadedFeedPage(List.of(), false, rankVersion.value(), null, 0L);
        }
        boolean hasLookahead = fetchedPosts.size() > limit;
        boolean hasNext = state.page() < FeedCursorCodec.MAX_CURSOR_PAGE && hasLookahead;
        List<DiscussPost> fallbackPosts = hasLookahead
                ? List.copyOf(fetchedPosts.subList(0, limit))
                : List.copyOf(fetchedPosts);
        String rankVersion = policyProperties.getHotRankVersion();
        safeWarmFeedCache(fallbackPosts, boardId, rankVersion);
        List<PostSummaryResult> items = filterBoardItems(postFeedSummaryLoader.assembleSummaries(fallbackPosts), boardId);
        safePutSummaryCache(fallbackPosts, items);
        hotFeedReadMetrics.record(cacheDegraded ? "degraded" : "fallback", scope);
        return new LoadedFeedPage(items, hasNext, rankVersion, postBoundaryOf(fallbackPosts), 0L);
    }

    private List<DiscussPost> listFallbackPosts(
            FeedCursorCodec.CursorState state,
            int limit,
            UUID boardId
    ) {
        int fetchLimit = limit + 1;
        if (state.hasHotBoundary()) {
            FeedCursorCodec.HotBoundary boundary = state.hotBoundary();
            return postContentRepository.listHotPostsAfter(
                    boundary.type(),
                    boundary.score(),
                    boundary.createTime(),
                    boundary.postId(),
                    fetchLimit,
                    boardId
            );
        }
        return postContentRepository.listPosts(
                state.page(),
                limit,
                fetchLimit,
                PostContentRepository.ORDER_HOT,
                boardId,
                null
        );
    }

    private static FeedCursorCodec.HotBoundary postBoundaryOf(List<DiscussPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return null;
        }
        DiscussPost last = posts.get(posts.size() - 1);
        if (last == null || last.getId() == null || last.getCreateTime() == null) {
            return null;
        }
        return new FeedCursorCodec.HotBoundary(last.getType(), last.getScore(), last.getCreateTime(), last.getId());
    }

    private static String boundaryKey(FeedCursorCodec.CursorState state) {
        if (!state.hasHotBoundary()) {
            return "offset";
        }
        FeedCursorCodec.HotBoundary boundary = state.hotBoundary();
        return boundary.type()
                + ":" + boundary.score()
                + ":" + boundary.createTime().getTime()
                + ":" + boundary.postId();
    }

    private RankVersionResult safeRankVersion() {
        try {
            return new RankVersionResult(postFeedCache.readRankVersion(), false);
        } catch (RuntimeException ex) {
            return new RankVersionResult(policyProperties.getHotRankVersion(), true);
        }
    }

    private void warmFeedCache(List<DiscussPost> posts, UUID boardId, String rankVersion) {
        postFeedCache.writeRankVersion(rankVersion);
        for (DiscussPost post : posts) {
            if (post == null || post.getId() == null) {
                continue;
            }
            if (boardId == null) {
                postFeedCache.upsertGlobalHot(
                        projectionEntry(post),
                        rankVersion,
                        post.getAggregateVersion(),
                        post.getScoreVersion()
                );
                continue;
            }
            postFeedCache.upsertBoardHot(
                    boardId,
                    projectionEntry(post),
                    rankVersion,
                    post.getAggregateVersion(),
                    post.getScoreVersion()
            );
        }
    }

    private void safeWarmFeedCache(List<DiscussPost> posts, UUID boardId, String rankVersion) {
        try {
            warmFeedCache(posts, boardId, rankVersion);
        } catch (RuntimeException ignored) {
            // Feed cache warm-up is best-effort for fallback reads.
        }
    }

    private static PostFeedCache.HotProjectionEntry projectionEntry(DiscussPost post) {
        return new PostFeedCache.HotProjectionEntry(
                post.getId(), post.getType(), post.getScore(), post.getCreateTime());
    }

    private void safePutSummaryCache(List<DiscussPost> posts, List<PostSummaryResult> items) {
        try {
            postFeedSummaryLoader.cacheSummaries(posts, items);
        } catch (RuntimeException ignored) {
            // Summary cache backfill is best-effort for fallback reads.
        }
    }

    private int normalizeRequestedSize(int size) {
        return Math.min(MAX_SIZE, Math.max(1, size <= 0 ? DEFAULT_SIZE : size));
    }

    private static int nextPage(int page) {
        return page >= Integer.MAX_VALUE ? Integer.MAX_VALUE : page + 1;
    }

    private record CachePageLoad(LoadedFeedPage page, boolean cacheDegraded, String result) {

        private static CachePageLoad page(LoadedFeedPage page, String result) {
            return new CachePageLoad(page, false, result);
        }

        private static CachePageLoad miss() {
            return new CachePageLoad(null, false, null);
        }

        private static CachePageLoad degradedMiss() {
            return new CachePageLoad(null, true, null);
        }
    }

    private record RankVersionResult(String value, boolean degraded) {
    }

    private record LoadedFeedPage(
            List<PostSummaryResult> items,
            boolean hasNext,
            String rankVersion,
            FeedCursorCodec.HotBoundary nextBoundary,
            long projectionEpoch
    ) {
    }
}
