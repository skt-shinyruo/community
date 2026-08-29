package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
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
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.application.PostReadTransactionOperations.DetailSnapshot;
import com.nowcoder.community.content.application.PostReadTransactionOperations.ProjectionBatchSnapshot;
import com.nowcoder.community.content.application.PostReadTransactionOperations.ProjectionSnapshot;
import com.nowcoder.community.content.application.PostReadTransactionOperations.SummarySnapshot;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.UNAUTHORIZED;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class PostReadApplicationService implements PostScanQueryApi, PostReadQueryApi {

    private static final int MAX_BATCH_POST_IDS = 200;

    private final PostContentRepository postContentPort;
    private final CommentContentRepository commentContentPort;
    private final SocialLikeQueryApi likeQueryService;
    private final TagContentRepository tagContentPort;
    private final BookmarkRepository bookmarkContentPort;
    private final SubscriptionRepository subscriptionContentPort;
    private final PostCounterApplicationService postCounterApplicationService;
    private final PostContentBlockRepository postContentBlockRepository;
    private final PostMediaAssetRepository postMediaAssetRepository;
    private final PostDetailCache postDetailCache;
    private final PostContentBlockTextProjector postContentBlockTextProjector;
    private final ContentTextCodec textCodec;
    private final PostFeedSummaryLoader postFeedSummaryLoader;
    private final PostDetailAssembler postDetailAssembler;
    private final RecentUserCommentAssembler recentUserCommentAssembler;
    private final ContentHotPathProperties hotPathProperties;
    private final HotPathSingleFlight hotPathSingleFlight;
    private final PostReadTransactionOperations readTransactionOperations;

    public PostReadApplicationService(
            PostContentRepository postContentPort,
            CommentContentRepository commentContentPort,
            SocialLikeQueryApi likeQueryService,
            TagContentRepository tagContentPort,
            BookmarkRepository bookmarkContentPort,
            SubscriptionRepository subscriptionContentPort,
            PostCounterApplicationService postCounterApplicationService,
            PostContentBlockRepository postContentBlockRepository,
            PostMediaAssetRepository postMediaAssetRepository,
            PostDetailCache postDetailCache,
            PostContentBlockTextProjector postContentBlockTextProjector,
            ContentTextCodec textCodec,
            PostFeedSummaryLoader postFeedSummaryLoader,
            PostDetailAssembler postDetailAssembler,
            RecentUserCommentAssembler recentUserCommentAssembler,
            PostReadTransactionOperations readTransactionOperations,
            ContentHotPathProperties hotPathProperties,
            HotPathSingleFlight hotPathSingleFlight
    ) {
        this.postContentPort = Objects.requireNonNull(postContentPort, "postContentPort");
        this.commentContentPort = Objects.requireNonNull(commentContentPort, "commentContentPort");
        this.likeQueryService = Objects.requireNonNull(likeQueryService, "likeQueryService");
        this.tagContentPort = Objects.requireNonNull(tagContentPort, "tagContentPort");
        this.bookmarkContentPort = Objects.requireNonNull(bookmarkContentPort, "bookmarkContentPort");
        this.subscriptionContentPort = Objects.requireNonNull(subscriptionContentPort, "subscriptionContentPort");
        this.postCounterApplicationService = Objects.requireNonNull(
                postCounterApplicationService, "postCounterApplicationService");
        this.postContentBlockRepository = Objects.requireNonNull(
                postContentBlockRepository, "postContentBlockRepository");
        this.postMediaAssetRepository = Objects.requireNonNull(
                postMediaAssetRepository, "postMediaAssetRepository");
        this.postDetailCache = Objects.requireNonNull(postDetailCache, "postDetailCache");
        this.postContentBlockTextProjector = Objects.requireNonNull(
                postContentBlockTextProjector, "postContentBlockTextProjector");
        this.textCodec = Objects.requireNonNull(textCodec, "textCodec");
        this.postFeedSummaryLoader = Objects.requireNonNull(postFeedSummaryLoader, "postFeedSummaryLoader");
        this.postDetailAssembler = Objects.requireNonNull(postDetailAssembler, "postDetailAssembler");
        this.recentUserCommentAssembler = Objects.requireNonNull(
                recentUserCommentAssembler, "recentUserCommentAssembler");
        this.readTransactionOperations = Objects.requireNonNull(
                readTransactionOperations, "readTransactionOperations");
        this.hotPathProperties = Objects.requireNonNull(hotPathProperties, "hotPathProperties");
        this.hotPathSingleFlight = Objects.requireNonNull(hotPathSingleFlight, "hotPathSingleFlight");
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

        return postFeedSummaryLoader.readSnapshot(snapshot);
    }

    @Override
    public List<PostSummaryView> listPostsByUser(UUID userId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 3 : size;
        return postFeedSummaryLoader.readSnapshot(readTransactionOperations.listPostsByUser(userId, p, s)).stream()
                .map(PostReadApplicationService::toPostSummaryView)
                .toList();
    }

    private static PostSummaryView toPostSummaryView(PostSummaryResult result) {
        return new PostSummaryView(
                result.id(),
                result.userId(),
                result.title(),
                result.type(),
                result.status(),
                result.createTime(),
                result.commentCount(),
                result.score(),
                result.categoryId(),
                result.tags(),
                result.lastReplyUserId(),
                result.lastReplyTime(),
                result.lastActivityTime(),
                result.lastReplyPreview()
        );
    }

    public List<PostSummaryResult> listPostsByIds(List<UUID> postIds) {
        if (postIds != null && postIds.size() > MAX_BATCH_POST_IDS) {
            throw new BusinessException(INVALID_ARGUMENT, "postIds cannot exceed " + MAX_BATCH_POST_IDS);
        }
        return postFeedSummaryLoader.readSnapshot(readTransactionOperations.listPostsByIds(postIds));
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
        boolean liked = currentUserId != null && likeQueryService.isLiked(currentUserId, EntityTypes.POST, detail.id());
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

    @Override
    public List<RecentUserCommentView> listRecentCommentsByUser(UUID userId, Integer page, Integer size) {
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

    private RecentUserCommentView toRecentComment(Comment comment, Map<UUID, DiscussPost> visiblePostsById) {
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

    private record VersionedPostDetail(PostDetailResult detail, long aggregateVersion) {
    }
}
