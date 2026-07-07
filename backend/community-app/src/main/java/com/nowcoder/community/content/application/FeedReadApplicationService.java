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

import java.util.List;
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

    @Autowired
    public FeedReadApplicationService(
            PostFeedCache postFeedCache,
            PostContentRepository postContentRepository,
            PostSummaryCache postSummaryCache,
            PostFeedSummaryLoader postFeedSummaryLoader,
            FeedCursorCodec feedCursorCodec,
            ContentFeedPolicyProperties policyProperties,
            HotFeedReadMetrics hotFeedReadMetrics
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
        LoadedFeedPage page = loadHotPage(cursor, state.page(), requestedLimit, boardId);
        String nextCursor = page.hasNext()
                ? feedCursorCodec.encodePage(state.page() + 1, requestedLimit)
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

    private LoadedFeedPage loadHotPage(String cursor, int page, int limit, UUID boardId) {
        String scope = boardId == null ? "global" : "board";
        String encodedCursor = feedCursorCodec.encodePage(page, limit);
        try {
            List<UUID> ids = readFeedIds(page == 0 ? cursor : encodedCursor, limit, boardId);
            if (!ids.isEmpty()) {
                hotFeedReadMetrics.record("hit", scope);
                List<PostSummaryResult> items = filterBoardItems(postFeedSummaryLoader.readSummaries(ids), boardId);
                return new LoadedFeedPage(items, hasNextCachedPage(page, limit, boardId), safeRankVersion());
            }
        } catch (RuntimeException ex) {
            hotFeedReadMetrics.record("degraded", scope);
        }
        if (!policyProperties.isLatestFallbackEnabled()) {
            return new LoadedFeedPage(List.of(), false, safeRankVersion());
        }
        List<DiscussPost> fallbackPosts = listFallbackPosts(page, limit, boardId);
        if (fallbackPosts.isEmpty()) {
            hotFeedReadMetrics.record("empty", scope);
            return new LoadedFeedPage(List.of(), false, safeRankVersion());
        }
        hotFeedReadMetrics.record("fallback", scope);
        String rankVersion = policyProperties.getHotRankVersion();
        warmFeedCache(fallbackPosts, boardId, rankVersion);
        List<PostSummaryResult> items = filterBoardItems(postFeedSummaryLoader.assembleSummaries(fallbackPosts), boardId);
        postSummaryCache.putAll(items);
        return new LoadedFeedPage(items, !listFallbackPosts(page + 1, limit, boardId).isEmpty(), rankVersion);
    }

    private boolean hasNextCachedPage(int page, int limit, UUID boardId) {
        String nextCursor = feedCursorCodec.encodePage(page + 1, limit);
        List<UUID> nextIds = readFeedIds(nextCursor, limit, boardId);
        return !nextIds.isEmpty();
    }

    private List<DiscussPost> listFallbackPosts(int page, int limit, UUID boardId) {
        if (boardId == null) {
            return postContentRepository.listPosts(page, limit, PostContentRepository.ORDER_HOT);
        }
        return postContentRepository.listPosts(page, limit, PostContentRepository.ORDER_HOT, boardId, null);
    }

    private String safeRankVersion() {
        try {
            return postFeedCache.readRankVersion();
        } catch (RuntimeException ex) {
            hotFeedReadMetrics.record("degraded", "rank_version");
            return policyProperties.getHotRankVersion();
        }
    }

    private void warmFeedCache(List<DiscussPost> posts, UUID boardId, String rankVersion) {
        postFeedCache.writeRankVersion(rankVersion);
        for (DiscussPost post : posts) {
            if (post == null || post.getId() == null) {
                continue;
            }
            if (boardId == null) {
                postFeedCache.upsertGlobalHot(post.getId(), post.getScore(), rankVersion);
                continue;
            }
            postFeedCache.upsertBoardHot(boardId, post.getId(), post.getScore(), rankVersion);
        }
    }

    private int normalizeRequestedSize(int size) {
        return Math.min(MAX_SIZE, Math.max(1, size <= 0 ? DEFAULT_SIZE : size));
    }
    private record LoadedFeedPage(List<PostSummaryResult> items, boolean hasNext, String rankVersion) {
    }
}
