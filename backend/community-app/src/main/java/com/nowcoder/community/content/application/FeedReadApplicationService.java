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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class FeedReadApplicationService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final String RANK_VERSION = "hot-v1";

    private final PostFeedCache postFeedCache;
    private final PostContentRepository postContentRepository;
    private final CommentContentRepository commentContentRepository;
    private final TagContentRepository tagContentRepository;
    private final PostContentBlockRepository postContentBlockRepository;
    private final PostSummaryCache postSummaryCache;
    private final PostContentBlockTextProjector postContentBlockTextProjector;
    private final PostSummaryAssembler postSummaryAssembler;
    private final FeedCursorCodec feedCursorCodec;

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
        this.postFeedCache = postFeedCache;
        this.postContentRepository = postContentRepository;
        this.commentContentRepository = commentContentRepository;
        this.tagContentRepository = tagContentRepository;
        this.postContentBlockRepository = postContentBlockRepository;
        this.postSummaryCache = postSummaryCache;
        this.postContentBlockTextProjector = postContentBlockTextProjector;
        this.postSummaryAssembler = postSummaryAssembler;
        this.feedCursorCodec = feedCursorCodec;
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
        return new FeedPageResult(page.items(), nextCursor, RANK_VERSION);
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

    private List<PostSummaryResult> readSummaries(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, PostSummaryResult> cachedEntries = postSummaryCache.getAll(postIds);
        Map<UUID, PostSummaryResult> cached = new java.util.LinkedHashMap<>(cachedEntries == null ? Map.of() : cachedEntries);
        List<UUID> missingIds = postIds.stream()
                .filter(id -> !cached.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            List<PostSummaryResult> loaded = assembleSummaries(postContentRepository.listPostsByIds(missingIds));
            postSummaryCache.putAll(loaded);
            loaded.forEach(item -> cached.put(item.id(), item));
        }
        return postIds.stream()
                .map(cached::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private LoadedFeedPage loadHotPage(String cursor, int page, int limit, UUID boardId) {
        String encodedCursor = feedCursorCodec.encodePage(page, limit);
        List<UUID> ids = readFeedIds(page == 0 ? cursor : encodedCursor, limit, boardId);
        if (!ids.isEmpty()) {
            List<PostSummaryResult> items = filterBoardItems(readSummaries(ids), boardId);
            return new LoadedFeedPage(items, hasNextCachedPage(page, limit, boardId));
        }
        List<DiscussPost> fallbackPosts = listFallbackPosts(page, limit, boardId);
        if (fallbackPosts.isEmpty()) {
            return new LoadedFeedPage(List.of(), false);
        }
        warmFeedCache(fallbackPosts, boardId);
        List<PostSummaryResult> items = filterBoardItems(assembleSummaries(fallbackPosts), boardId);
        postSummaryCache.putAll(items);
        return new LoadedFeedPage(items, !listFallbackPosts(page + 1, limit, boardId).isEmpty());
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

    private void warmFeedCache(List<DiscussPost> posts, UUID boardId) {
        for (DiscussPost post : posts) {
            if (post == null || post.getId() == null) {
                continue;
            }
            if (boardId == null) {
                postFeedCache.upsertGlobalHot(post.getId(), post.getScore(), RANK_VERSION);
                continue;
            }
            postFeedCache.upsertBoardHot(boardId, post.getId(), post.getScore(), RANK_VERSION);
        }
    }

    private int normalizeRequestedSize(int size) {
        return Math.min(MAX_SIZE, Math.max(1, size <= 0 ? DEFAULT_SIZE : size));
    }

    private List<PostSummaryResult> assembleSummaries(List<DiscussPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<UUID, Comment> lastActivities = commentContentRepository.getLatestPostActivitiesByPostIds(postIds);
        Map<UUID, List<String>> tagsByPostId = tagContentRepository.getTagsByPostIds(postIds);
        Map<UUID, List<PostContentBlock>> blocksByPostId = postContentBlockRepository.listByPostIds(postIds);
        return posts.stream()
                .map(post -> postSummaryAssembler.assemble(
                        post,
                        lastActivities.get(post.getId()),
                        tagsByPostId.get(post.getId()),
                        postContentBlockTextProjector.preview(blocksByPostId.get(post.getId()), 240)
                ))
                .toList();
    }

    private record LoadedFeedPage(List<PostSummaryResult> items, boolean hasNext) {
    }
}
