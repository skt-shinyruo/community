package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.FeedPageResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FeedReadApplicationService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final PostFeedCache postFeedCache;
    private final PostContentRepository postContentRepository;
    private final PostSummaryCache postSummaryCache;
    private final PostFeedSummaryLoader postFeedSummaryLoader;
    private final FeedCursorCodec feedCursorCodec;
    private final ContentFeedPolicyProperties policyProperties;
    private final HotFeedReadMetrics hotFeedReadMetrics;
    private final ContentHotPathProperties hotPathProperties;
    private final HotPathSingleFlight hotPathSingleFlight;

    @Autowired
    public FeedReadApplicationService(
            PostFeedCache postFeedCache,
            PostContentRepository postContentRepository,
            PostSummaryCache postSummaryCache,
            PostFeedSummaryLoader postFeedSummaryLoader,
            FeedCursorCodec feedCursorCodec,
            ContentFeedPolicyProperties policyProperties,
            HotFeedReadMetrics hotFeedReadMetrics,
            ContentHotPathProperties hotPathProperties,
            HotPathSingleFlight hotPathSingleFlight
    ) {
        this.postFeedCache = postFeedCache;
        this.postContentRepository = postContentRepository;
        this.postSummaryCache = postSummaryCache;
        this.postFeedSummaryLoader = postFeedSummaryLoader;
        this.feedCursorCodec = feedCursorCodec;
        this.policyProperties = policyProperties == null ? new ContentFeedPolicyProperties() : policyProperties;
        this.hotFeedReadMetrics = hotFeedReadMetrics == null
                ? new HotFeedReadMetrics((MeterRegistry) null)
                : hotFeedReadMetrics;
        this.hotPathProperties = hotPathProperties == null ? new ContentHotPathProperties() : hotPathProperties;
        this.hotPathSingleFlight = hotPathSingleFlight == null ? loaderSingleFlight() : hotPathSingleFlight;
    }

    public FeedReadApplicationService(
            PostFeedCache postFeedCache,
            PostContentRepository postContentRepository,
            PostSummaryCache postSummaryCache,
            PostFeedSummaryLoader postFeedSummaryLoader,
            FeedCursorCodec feedCursorCodec,
            ContentFeedPolicyProperties policyProperties,
            HotFeedReadMetrics hotFeedReadMetrics
    ) {
        this(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
                postFeedSummaryLoader,
                feedCursorCodec,
                policyProperties,
                hotFeedReadMetrics,
                new ContentHotPathProperties(),
                loaderSingleFlight()
        );
    }

    public FeedReadApplicationService(
            PostFeedCache postFeedCache,
            PostContentRepository postContentRepository,
            CommentContentRepository commentContentRepository,
            TagContentRepository tagContentRepository,
            PostContentBlockRepository postContentBlockRepository,
            PostSummaryCache postSummaryCache,
            PostContentBlockTextProjector postContentBlockTextProjector,
            PostSummaryAssembler postSummaryAssembler,
            FeedCursorCodec feedCursorCodec,
            ContentFeedPolicyProperties policyProperties,
            HotFeedReadMetrics hotFeedReadMetrics
    ) {
        this(
                postFeedCache,
                postContentRepository,
                postSummaryCache,
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
                hotFeedReadMetrics
        );
    }

    public FeedReadApplicationService(
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
        this(
                postFeedCache,
                postContentRepository,
                commentContentRepository,
                tagContentRepository,
                postContentBlockRepository,
                postSummaryCache,
                postContentBlockTextProjector,
                postSummaryAssembler,
                feedCursorCodec,
                policyProperties,
                new HotFeedReadMetrics((MeterRegistry) null)
        );
    }

    public FeedReadApplicationService(
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
        this(
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
        String nextCursor = page.hasNext()
                ? feedCursorCodec.encodeHotPage(nextPage(state.page()), requestedLimit, page.nextBoundary())
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

    private List<UUID> readFeedIds(String cursor, int size, UUID boardId) {
        if (boardId == null) {
            List<UUID> ids = postFeedCache.readGlobalHotIds(cursor, size);
            return ids == null ? List.of() : ids;
        }
        List<UUID> ids = postFeedCache.readBoardHotIds(boardId, cursor, size);
        return ids == null ? List.of() : ids;
    }

    private LoadedFeedPage loadHotPage(String cursor, FeedCursorCodec.CursorState state, int limit, UUID boardId) {
        String scope = boardId == null ? "global" : "board";
        String encodedCursor = feedCursorCodec.encodePage(state.page(), limit);
        CachePageLoad cachePageLoad = loadCachePage(
                state.page() == 0 ? cursor : encodedCursor,
                state,
                limit,
                boardId
        );
        if (cachePageLoad.page() != null) {
            hotFeedReadMetrics.record(cachePageLoad.result(), scope);
            return cachePageLoad.page();
        }
        return loadFallbackPage(state, limit, boardId, scope, cachePageLoad.cacheDegraded());
    }

    private CachePageLoad loadCachePage(
            String cursor,
            FeedCursorCodec.CursorState state,
            int limit,
            UUID boardId
    ) {
        List<UUID> ids;
        try {
            ids = readFeedIds(cursor, limit, boardId);
        } catch (RuntimeException ex) {
            return CachePageLoad.degradedMiss();
        }
        if (ids.isEmpty()) {
            return CachePageLoad.miss();
        }
        List<PostSummaryResult> items;
        try {
            items = postFeedSummaryLoader.readSummaries(ids);
        } catch (RuntimeException ex) {
            return CachePageLoad.miss();
        }
        boolean hasNext;
        try {
            hasNext = hasNextCachedPage(state.page(), limit, boardId);
        } catch (RuntimeException ex) {
            return CachePageLoad.degradedMiss();
        }
        RankVersionResult rankVersion = safeRankVersion();
        String result = rankVersion.degraded() ? "degraded" : "hit";
        if (!hasNext && !state.hasHotBoundary()) {
            return CachePageLoad.page(
                    new LoadedFeedPage(filterBoardItems(items, boardId), false, rankVersion.value(), null),
                    result
            );
        }
        AuthoritativeCachePage authoritativePage;
        try {
            authoritativePage = authoritativeCachePage(ids, state, limit, boardId);
        } catch (RuntimeException ex) {
            return CachePageLoad.degradedMiss();
        }
        if (authoritativePage == null || authoritativePage.posts().isEmpty()) {
            // A cache member can outlive a deleted/moved post. Let the SQL keyset query
            // reconstruct the page instead of issuing a cursor from stale metadata.
            return CachePageLoad.miss();
        }
        List<DiscussPost> authoritativePosts = authoritativePage.posts();
        Map<UUID, PostSummaryResult> summariesById = new HashMap<>();
        for (PostSummaryResult item : items) {
            if (item != null && item.id() != null) {
                summariesById.put(item.id(), item);
            }
        }
        List<PostSummaryResult> authoritativeItems = new ArrayList<>(authoritativePosts.size());
        for (DiscussPost post : authoritativePosts) {
            PostSummaryResult summary = summariesById.get(post.getId());
            if (summary == null) {
                return CachePageLoad.miss();
            }
            // Summary content may legitimately be stale, but rank and board fields must
            // come from the row that produced the cursor.
            authoritativeItems.add(mergeRankFields(summary, post));
        }
        return CachePageLoad.page(
                new LoadedFeedPage(
                        authoritativeItems,
                        authoritativePage.hasNext(),
                        rankVersion.value(),
                        postBoundaryOf(authoritativePosts)
                ),
                result
        );
    }

    /**
     * Redis stores a compact score/member projection. Validate that the IDs it returned are
     * still in the database's complete hot-feed order before trusting a page boundary.
     */
    private AuthoritativeCachePage authoritativeCachePage(
            List<UUID> ids,
            FeedCursorCodec.CursorState state,
            int limit,
            UUID boardId
    ) {
        List<UUID> distinctIds = ids.stream().filter(id -> id != null).distinct().toList();
        if (distinctIds.size() != ids.size()) {
            return null;
        }
        List<DiscussPost> loaded = listFallbackPosts(state, limit, boardId);
        if (loaded == null || loaded.isEmpty()) {
            return null;
        }
        boolean hasNext = loaded.size() > limit;
        List<DiscussPost> authoritativePage = hasNext
                ? List.copyOf(loaded.subList(0, limit))
                : List.copyOf(loaded);
        if (authoritativePage.size() != distinctIds.size()) {
            return null;
        }
        for (DiscussPost post : authoritativePage) {
            if (post == null
                    || post.getId() == null
                    || post.getCreateTime() == null
                    || post.isDeleted()
                    || !Double.isFinite(post.getScore())) {
                return null;
            }
            if (boardId != null && !boardId.equals(post.getCategoryId())) {
                return null;
            }
        }
        List<UUID> authoritativeIds = authoritativePage.stream().map(DiscussPost::getId).toList();
        return authoritativeIds.equals(distinctIds)
                ? new AuthoritativeCachePage(authoritativePage, hasNext)
                : null;
    }

    private static PostSummaryResult mergeRankFields(PostSummaryResult summary, DiscussPost post) {
        return new PostSummaryResult(
                post.getId(),
                summary.userId() == null ? post.getUserId() : summary.userId(),
                summary.title(),
                summary.preview(),
                post.getType(),
                post.getStatus(),
                post.getCreateTime(),
                post.getCommentCount(),
                post.getScore(),
                post.getCategoryId(),
                summary.tags(),
                summary.lastReplyUserId(),
                summary.lastReplyTime(),
                summary.lastActivityTime(),
                summary.lastReplyPreview()
        );
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
                    return new LoadedFeedPage(List.of(), false, rankVersion.value(), null);
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
            return new LoadedFeedPage(List.of(), false, rankVersion.value(), null);
        }
        List<DiscussPost> fetchedPosts = listFallbackPosts(state, limit, boardId);
        if (fetchedPosts.isEmpty()) {
            RankVersionResult rankVersion = safeRankVersion();
            hotFeedReadMetrics.record(cacheDegraded || rankVersion.degraded() ? "degraded" : "empty", scope);
            return new LoadedFeedPage(List.of(), false, rankVersion.value(), null);
        }
        boolean hasNext = fetchedPosts.size() > limit;
        List<DiscussPost> fallbackPosts = hasNext
                ? List.copyOf(fetchedPosts.subList(0, limit))
                : List.copyOf(fetchedPosts);
        String rankVersion = policyProperties.getHotRankVersion();
        safeWarmFeedCache(fallbackPosts, boardId, rankVersion);
        List<PostSummaryResult> items = filterBoardItems(postFeedSummaryLoader.assembleSummaries(fallbackPosts), boardId);
        safePutSummaryCache(fallbackPosts, items);
        hotFeedReadMetrics.record(cacheDegraded ? "degraded" : "fallback", scope);
        return new LoadedFeedPage(items, hasNext, rankVersion, postBoundaryOf(fallbackPosts));
    }

    private boolean hasNextCachedPage(int page, int limit, UUID boardId) {
        if (page >= Integer.MAX_VALUE) {
            return false;
        }
        String nextCursor = feedCursorCodec.encodePage(nextPage(page), limit);
        List<UUID> nextIds = readFeedIds(nextCursor, limit, boardId);
        return !nextIds.isEmpty();
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

    private static FeedCursorCodec.HotBoundary summaryBoundaryOf(List<PostSummaryResult> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        PostSummaryResult last = items.get(items.size() - 1);
        if (last == null || last.id() == null || last.createTime() == null) {
            return null;
        }
        return new FeedCursorCodec.HotBoundary(last.type(), last.score(), last.createTime(), last.id());
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
                        post.getId(),
                        post.getScore(),
                        rankVersion,
                        post.getAggregateVersion(),
                        post.getScoreVersion()
                );
                continue;
            }
            postFeedCache.upsertBoardHot(
                    boardId,
                    post.getId(),
                    post.getScore(),
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

    private static HotPathSingleFlight loaderSingleFlight() {
        return new HotPathSingleFlight() {
            @Override
            public <T> T execute(String scope, String key, java.time.Duration ttl, java.util.function.Supplier<T> loader, java.util.function.Supplier<T> fallbackWhenBusy) {
                return loader.get();
            }
        };
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

    private record AuthoritativeCachePage(List<DiscussPost> posts, boolean hasNext) {
    }

    private record LoadedFeedPage(
            List<PostSummaryResult> items,
            boolean hasNext,
            String rankVersion,
            FeedCursorCodec.HotBoundary nextBoundary
    ) {
    }
}
