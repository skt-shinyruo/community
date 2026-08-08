package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.domain.repository.SubscriptionRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.application.result.RecentUserCommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.application.PostReadTransactionOperations.DetailSnapshot;
import com.nowcoder.community.content.application.PostReadTransactionOperations.ProjectionBatchSnapshot;
import com.nowcoder.community.content.application.PostReadTransactionOperations.ProjectionSnapshot;
import com.nowcoder.community.content.application.PostReadTransactionOperations.SummarySnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.UNAUTHORIZED;

@Service
public class PostReadApplicationService implements PostScanQueryApi {

    private final PostContentRepository postContentPort;
    private final CommentContentRepository commentContentPort;
    private final LikeQueryPort likeQueryService;
    private final TagContentRepository tagContentPort;
    private final BookmarkRepository bookmarkContentPort;
    private final SubscriptionRepository subscriptionContentPort;
    private final PostCounterApplicationService postCounterApplicationService;
    private final PostContentBlockRepository postContentBlockRepository;
    private final PostMediaAssetRepository postMediaAssetRepository;
    private final PostDetailCache postDetailCache;
    private final PostContentBlockTextProjector postContentBlockTextProjector;
    private final ContentTextCodec textCodec;
    private final PostSummaryAssembler postSummaryAssembler;
    private final PostDetailAssembler postDetailAssembler;
    private final RecentUserCommentAssembler recentUserCommentAssembler;
    private final ContentHotPathProperties hotPathProperties;
    private final HotPathSingleFlight hotPathSingleFlight;
    private final PostReadTransactionOperations readTransactionOperations;

    @Autowired
    public PostReadApplicationService(
            PostContentRepository postContentPort,
            CommentContentRepository commentContentPort,
            LikeQueryPort likeQueryService,
            TagContentRepository tagContentPort,
            BookmarkRepository bookmarkContentPort,
            SubscriptionRepository subscriptionContentPort,
            PostCounterApplicationService postCounterApplicationService,
            PostContentBlockRepository postContentBlockRepository,
            PostMediaAssetRepository postMediaAssetRepository,
            PostDetailCache postDetailCache,
            PostContentBlockTextProjector postContentBlockTextProjector,
            ContentTextCodec textCodec,
            PostSummaryAssembler postSummaryAssembler,
            PostDetailAssembler postDetailAssembler,
            RecentUserCommentAssembler recentUserCommentAssembler,
            PostReadTransactionOperations readTransactionOperations,
            ContentHotPathProperties hotPathProperties,
            HotPathSingleFlight hotPathSingleFlight
    ) {
        this.postContentPort = postContentPort;
        this.commentContentPort = commentContentPort;
        this.likeQueryService = likeQueryService;
        this.tagContentPort = tagContentPort;
        this.bookmarkContentPort = bookmarkContentPort;
        this.subscriptionContentPort = subscriptionContentPort;
        this.postCounterApplicationService = postCounterApplicationService;
        this.postContentBlockRepository = postContentBlockRepository;
        this.postMediaAssetRepository = postMediaAssetRepository;
        this.postDetailCache = postDetailCache;
        this.postContentBlockTextProjector = postContentBlockTextProjector;
        this.textCodec = textCodec;
        this.postSummaryAssembler = postSummaryAssembler;
        this.postDetailAssembler = postDetailAssembler;
        this.recentUserCommentAssembler = recentUserCommentAssembler;
        this.readTransactionOperations = readTransactionOperations;
        this.hotPathProperties = hotPathProperties == null ? new ContentHotPathProperties() : hotPathProperties;
        this.hotPathSingleFlight = hotPathSingleFlight == null ? loaderSingleFlight() : hotPathSingleFlight;
    }

    public PostReadApplicationService(
            PostContentRepository postContentPort,
            CommentContentRepository commentContentPort,
            LikeQueryPort likeQueryService,
            TagContentRepository tagContentPort,
            BookmarkRepository bookmarkContentPort,
            SubscriptionRepository subscriptionContentPort,
            PostCounterApplicationService postCounterApplicationService,
            PostContentBlockRepository postContentBlockRepository,
            PostMediaAssetRepository postMediaAssetRepository,
            PostDetailCache postDetailCache,
            PostContentBlockTextProjector postContentBlockTextProjector,
            ContentTextCodec textCodec,
            PostSummaryAssembler postSummaryAssembler,
            PostDetailAssembler postDetailAssembler,
            RecentUserCommentAssembler recentUserCommentAssembler,
            ContentHotPathProperties hotPathProperties,
            HotPathSingleFlight hotPathSingleFlight
    ) {
        this(
                postContentPort,
                commentContentPort,
                likeQueryService,
                tagContentPort,
                bookmarkContentPort,
                subscriptionContentPort,
                postCounterApplicationService,
                postContentBlockRepository,
                postMediaAssetRepository,
                postDetailCache,
                postContentBlockTextProjector,
                textCodec,
                postSummaryAssembler,
                postDetailAssembler,
                recentUserCommentAssembler,
                new PostReadTransactionOperations(
                        postContentPort,
                        commentContentPort,
                        tagContentPort,
                        postContentBlockRepository,
                        postMediaAssetRepository
                ),
                hotPathProperties,
                hotPathSingleFlight
        );
    }

    public PostReadApplicationService(
            PostContentRepository postContentPort,
            CommentContentRepository commentContentPort,
            LikeQueryPort likeQueryService,
            TagContentRepository tagContentPort,
            BookmarkRepository bookmarkContentPort,
            SubscriptionRepository subscriptionContentPort,
            PostCounterApplicationService postCounterApplicationService,
            PostContentBlockRepository postContentBlockRepository,
            PostMediaAssetRepository postMediaAssetRepository,
            PostDetailCache postDetailCache,
            PostContentBlockTextProjector postContentBlockTextProjector,
            ContentTextCodec textCodec,
            PostSummaryAssembler postSummaryAssembler,
            PostDetailAssembler postDetailAssembler,
            RecentUserCommentAssembler recentUserCommentAssembler
    ) {
        this(
                postContentPort,
                commentContentPort,
                likeQueryService,
                tagContentPort,
                bookmarkContentPort,
                subscriptionContentPort,
                postCounterApplicationService,
                postContentBlockRepository,
                postMediaAssetRepository,
                postDetailCache,
                postContentBlockTextProjector,
                textCodec,
                postSummaryAssembler,
                postDetailAssembler,
                recentUserCommentAssembler,
                new PostReadTransactionOperations(
                        postContentPort,
                        commentContentPort,
                        tagContentPort,
                        postContentBlockRepository,
                        postMediaAssetRepository
                ),
                new ContentHotPathProperties(),
                loaderSingleFlight()
        );
    }

    public List<PostSummaryResult> listPosts(UUID currentUserId, String order, UUID categoryId, String tag, Boolean subscribed, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        int orderMode = "hot".equalsIgnoreCase(order) ? PostContentRepository.ORDER_HOT : PostContentRepository.ORDER_LATEST;

        SummarySnapshot snapshot;
        if (Boolean.TRUE.equals(subscribed)) {
            if (currentUserId == null) {
                throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
            }
            List<UUID> subscribedCategoryIds = subscriptionContentPort.listSubscribedCategoryIds(currentUserId);
            snapshot = readTransactionOperations.listSubscribedPosts(
                    currentUserId,
                    subscribedCategoryIds,
                    p,
                    s,
                    orderMode,
                    categoryId,
                    tag
            );
        } else {
            snapshot = readTransactionOperations.listPosts(p, s, orderMode, categoryId, tag);
        }

        return assembleSummaries(snapshot);
    }

    public List<PostSummaryResult> listPostsByUser(UUID userId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 3 : size;
        return assembleSummaries(readTransactionOperations.listPostsByUser(userId, p, s));
    }

    public List<PostSummaryResult> listPostsByIds(List<UUID> postIds) {
        return assembleSummaries(readTransactionOperations.listPostsByIds(postIds));
    }

    public PostDetailResult getPostDetail(UUID currentUserId, UUID postId) {
        PostDetailResult cached = safeGetDetailCache(postId);
        if (cached != null) {
            return applyViewerOverlay(currentUserId, applyCounterOverlay(cached));
        }
        VersionedPostDetail loaded = hotPathSingleFlight.execute(
                "post_detail",
                postId.toString(),
                hotPathProperties.getSingleFlight().ttl(),
                () -> loadPostDetailShell(postId),
                () -> loadPostDetailShell(postId)
        );
        safePutDetailCache(postId, loaded.detail(), loaded.aggregateVersion());
        return applyViewerOverlay(currentUserId, applyCounterOverlay(loaded.detail()));
    }

    private PostDetailResult safeGetDetailCache(UUID postId) {
        try {
            return postDetailCache.get(postId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void safePutDetailCache(UUID postId, PostDetailResult detail, long aggregateVersion) {
        try {
            postDetailCache.put(postId, detail, aggregateVersion);
        } catch (RuntimeException ignored) {
            // Detail cache writes are best-effort for read responses.
        }
    }

    private VersionedPostDetail loadPostDetailShell(UUID postId) {
        DetailSnapshot snapshot = readTransactionOperations.getDetail(postId);
        DiscussPost post = snapshot.post();
        PostDetailResult detail = postDetailAssembler.assemble(
                post,
                snapshot.blocks(),
                snapshot.mediaAssets(),
                snapshot.tags(),
                0L,
                false,
                false
        );
        return new VersionedPostDetail(detail, post.getAggregateVersion());
    }

    private PostDetailResult applyCounterOverlay(PostDetailResult detail) {
        if (detail == null) {
            return null;
        }
        PostCounterSnapshot counters;
        try {
            counters = postCounterApplicationService.read(detail.id());
        } catch (RuntimeException ignored) {
            return detail;
        }
        return new PostDetailResult(
                detail.id(),
                detail.userId(),
                detail.title(),
                detail.blocks(),
                detail.type(),
                detail.status(),
                detail.createTime(),
                detail.updateTime(),
                detail.editCount(),
                toIntCount(counters.commentCount(), detail.commentCount()),
                counters.score(),
                detail.categoryId(),
                detail.tags(),
                counters.likeCount(),
                detail.liked(),
                detail.bookmarked()
        );
    }

    private PostDetailResult applyViewerOverlay(UUID currentUserId, PostDetailResult detail) {
        if (detail == null || currentUserId == null) {
            return detail;
        }
        boolean liked = likeQueryService.hasLikedPost(currentUserId, detail.id());
        boolean bookmarked = bookmarkContentPort.hasBookmarked(currentUserId, detail.id());
        return new PostDetailResult(
                detail.id(),
                detail.userId(),
                detail.title(),
                detail.blocks(),
                detail.type(),
                detail.status(),
                detail.createTime(),
                detail.updateTime(),
                detail.editCount(),
                detail.commentCount(),
                detail.score(),
                detail.categoryId(),
                detail.tags(),
                detail.likeCount(),
                liked,
                bookmarked
        );
    }

    public List<RecentUserCommentResult> listRecentCommentsByUser(UUID userId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 3 : size;
        List<Comment> comments = commentContentPort.listRecentCommentsByUser(userId, p, s);
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }

        List<UUID> postIds = comments.stream()
                .filter(comment -> comment != null && comment.getPostId() != null)
                .map(Comment::getPostId)
                .distinct()
                .toList();
        if (postIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, DiscussPost> visiblePostsById = new HashMap<>();
        for (DiscussPost post : postContentPort.listPostsByIds(postIds)) {
            if (post != null && post.getId() != null) {
                visiblePostsById.putIfAbsent(post.getId(), post);
            }
        }
        return comments.stream()
                .map(comment -> toRecentComment(comment, visiblePostsById))
                .filter(view -> view != null)
                .toList();
    }

    @Override
    public PostScanView scanPosts(UUID afterId, int limit) {
        int safeLimit = limit <= 0 ? 500 : Math.min(1000, Math.max(1, limit));
        ProjectionBatchSnapshot snapshot = readTransactionOperations.scanPosts(afterId, safeLimit);
        List<DiscussPost> posts = snapshot.posts();
        List<PostScanView.PostProjectionView> items = toPostProjectionResults(snapshot);
        UUID nextAfterId = posts.isEmpty() ? afterId : posts.get(posts.size() - 1).getId();
        return new PostScanView(items, nextAfterId, posts.size() == safeLimit);
    }

    @Override
    public PostScanView.PostProjectionView getPostProjectionAllowDeleted(UUID postId) {
        if (postId == null) {
            return null;
        }
        return toPostProjectionResult(readTransactionOperations.getProjectionAllowDeleted(postId));
    }

    private List<PostSummaryResult> assembleSummaries(SummarySnapshot snapshot) {
        List<DiscussPost> posts = snapshot.posts();
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return posts.stream()
                .map(post -> postSummaryAssembler.assemble(
                        post,
                        snapshot.lastActivities().get(post.getId()),
                        snapshot.tagsByPostId().get(post.getId()),
                        postContentBlockTextProjector.preview(snapshot.blocksByPostId().get(post.getId()), 240)
                ))
                .toList();
    }

    private List<PostScanView.PostProjectionView> toPostProjectionResults(ProjectionBatchSnapshot snapshot) {
        List<DiscussPost> posts = snapshot.posts();
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return posts.stream()
                .map(post -> toPostProjectionResult(
                        post,
                        snapshot.tagsByPostId().getOrDefault(post.getId(), List.of()),
                        snapshot.blocksByPostId().getOrDefault(post.getId(), List.of())
                ))
                .toList();
    }

    private PostScanView.PostProjectionView toPostProjectionResult(ProjectionSnapshot snapshot) {
        return toPostProjectionResult(snapshot.post(), snapshot.tags(), snapshot.blocks());
    }

    private PostScanView.PostProjectionView toPostProjectionResult(
            DiscussPost post,
            List<String> tags,
            List<PostContentBlock> blocks
    ) {
        return new PostScanView.PostProjectionView(
                post.getId(),
                post.getUserId(),
                post.getCategoryId(),
                tags,
                textCodec.decodeOnRead(post.getTitle()),
                textCodec.decodeOnRead(postContentBlockTextProjector.fullText(blocks)),
                post.getType(),
                post.getStatus(),
                post.getAggregateVersion(),
                post.getScoreVersion(),
                post.getCreateTime() == null ? null : post.getCreateTime().toInstant(),
                post.getScore()
        );
    }

    private RecentUserCommentResult toRecentComment(Comment comment, Map<UUID, DiscussPost> visiblePostsById) {
        if (comment == null || comment.getId() == null || comment.getPostId() == null) {
            return null;
        }
        UUID postId = comment.getPostId();
        DiscussPost post = visiblePostsById.get(postId);
        if (post == null) {
            return null;
        }
        return recentUserCommentAssembler.assemble(comment, postId, post.getTitle());
    }

    private static int toIntCount(long rawCount, int fallback) {
        if (rawCount <= 0L) {
            return Math.max(0, fallback);
        }
        return rawCount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rawCount;
    }

    private static HotPathSingleFlight loaderSingleFlight() {
        return new HotPathSingleFlight() {
            @Override
            public <T> T execute(String scope, String key, java.time.Duration ttl, java.util.function.Supplier<T> loader, java.util.function.Supplier<T> fallbackWhenBusy) {
                return loader.get();
            }
        };
    }

    private record VersionedPostDetail(PostDetailResult detail, long aggregateVersion) {
    }
}
