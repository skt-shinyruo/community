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
    private static final String RANK_VERSION = "db-fallback-v1";

    private final PostContentRepository postContentRepository;
    private final CommentContentRepository commentContentRepository;
    private final TagContentRepository tagContentRepository;
    private final PostContentBlockRepository postContentBlockRepository;
    private final PostSummaryCache postSummaryCache;
    private final PostContentBlockTextProjector postContentBlockTextProjector;
    private final PostSummaryAssembler postSummaryAssembler;
    private final FeedCursorCodec feedCursorCodec;

    public FeedReadApplicationService(
            PostContentRepository postContentRepository,
            CommentContentRepository commentContentRepository,
            TagContentRepository tagContentRepository,
            PostContentBlockRepository postContentBlockRepository,
            PostSummaryCache postSummaryCache,
            PostContentBlockTextProjector postContentBlockTextProjector,
            PostSummaryAssembler postSummaryAssembler,
            FeedCursorCodec feedCursorCodec
    ) {
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
        int requestedLimit = normalizeRequestedSize(size);
        FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
        int limit = state.size() > 0 ? normalizeRequestedSize(state.size()) : requestedLimit;
        int page = state.page();
        List<DiscussPost> rows = postContentRepository.listPosts(page, limit, PostContentRepository.ORDER_HOT, boardId, null);
        List<PostSummaryResult> items = readSummaries(rows);
        String nextCursor = hasNextPage(rows, page, limit, boardId) ? feedCursorCodec.encodePage(page + 1, limit) : "";
        return new FeedPageResult(items, nextCursor, RANK_VERSION);
    }

    private List<PostSummaryResult> readSummaries(List<DiscussPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<UUID, PostSummaryResult> cachedEntries = postSummaryCache.getAll(postIds);
        Map<UUID, PostSummaryResult> cached = new java.util.LinkedHashMap<>(cachedEntries == null ? Map.of() : cachedEntries);
        List<DiscussPost> missingPosts = posts.stream()
                .filter(post -> !cached.containsKey(post.getId()))
                .toList();
        if (!missingPosts.isEmpty()) {
            List<PostSummaryResult> loaded = assembleSummaries(missingPosts);
            postSummaryCache.putAll(loaded);
            loaded.forEach(item -> cached.put(item.id(), item));
        }
        return postIds.stream()
                .map(cached::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean hasNextPage(List<DiscussPost> rows, int page, int limit, UUID boardId) {
        if (rows == null || rows.size() < limit) {
            return false;
        }
        List<DiscussPost> nextRows = postContentRepository.listPosts(page + 1, limit, PostContentRepository.ORDER_HOT, boardId, null);
        return nextRows != null && !nextRows.isEmpty();
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
}
