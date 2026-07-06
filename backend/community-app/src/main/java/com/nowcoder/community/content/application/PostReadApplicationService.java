package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.domain.repository.SubscriptionRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostScanResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.application.result.RecentUserCommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.UNAUTHORIZED;

@Service
public class PostReadApplicationService {

    private final PostContentRepository postContentPort;
    private final CommentContentRepository commentContentPort;
    private final LikeQueryPort likeQueryService;
    private final TagContentRepository tagContentPort;
    private final BookmarkRepository bookmarkContentPort;
    private final SubscriptionRepository subscriptionContentPort;
    private final PostContentBlockRepository postContentBlockRepository;
    private final PostMediaAssetRepository postMediaAssetRepository;
    private final PostDetailCache postDetailCache;
    private final PostContentBlockTextProjector postContentBlockTextProjector;
    private final ContentTextCodec textCodec;
    private final PostSummaryAssembler postSummaryAssembler;
    private final PostDetailAssembler postDetailAssembler;
    private final RecentUserCommentAssembler recentUserCommentAssembler;

    public PostReadApplicationService(
            PostContentRepository postContentPort,
            CommentContentRepository commentContentPort,
            LikeQueryPort likeQueryService,
            TagContentRepository tagContentPort,
            BookmarkRepository bookmarkContentPort,
            SubscriptionRepository subscriptionContentPort,
            PostContentBlockRepository postContentBlockRepository,
            PostMediaAssetRepository postMediaAssetRepository,
            PostDetailCache postDetailCache,
            PostContentBlockTextProjector postContentBlockTextProjector,
            ContentTextCodec textCodec,
            PostSummaryAssembler postSummaryAssembler,
            PostDetailAssembler postDetailAssembler,
            RecentUserCommentAssembler recentUserCommentAssembler
    ) {
        this.postContentPort = postContentPort;
        this.commentContentPort = commentContentPort;
        this.likeQueryService = likeQueryService;
        this.tagContentPort = tagContentPort;
        this.bookmarkContentPort = bookmarkContentPort;
        this.subscriptionContentPort = subscriptionContentPort;
        this.postContentBlockRepository = postContentBlockRepository;
        this.postMediaAssetRepository = postMediaAssetRepository;
        this.postDetailCache = postDetailCache;
        this.postContentBlockTextProjector = postContentBlockTextProjector;
        this.textCodec = textCodec;
        this.postSummaryAssembler = postSummaryAssembler;
        this.postDetailAssembler = postDetailAssembler;
        this.recentUserCommentAssembler = recentUserCommentAssembler;
    }

    public List<PostSummaryResult> listPosts(UUID currentUserId, String order, UUID categoryId, String tag, Boolean subscribed, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        int orderMode = "hot".equalsIgnoreCase(order) ? PostContentRepository.ORDER_HOT : PostContentRepository.ORDER_LATEST;

        List<DiscussPost> posts;
        if (Boolean.TRUE.equals(subscribed)) {
            if (currentUserId == null) {
                throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
            }
            List<UUID> subscribedCategoryIds = subscriptionContentPort.listSubscribedCategoryIds(currentUserId);
            posts = postContentPort.listSubscribedPosts(currentUserId, subscribedCategoryIds, p, s, orderMode, categoryId, tag);
        } else {
            posts = postContentPort.listPosts(p, s, orderMode, categoryId, tag);
        }

        return assembleSummaries(posts);
    }

    public List<PostSummaryResult> listPostsByUser(UUID userId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 3 : size;
        return assembleSummaries(postContentPort.listPostsByUser(userId, p, s));
    }

    public List<PostSummaryResult> listPostsByIds(List<UUID> postIds) {
        return assembleSummaries(postContentPort.listPostsByIds(postIds));
    }

    public PostDetailResult getPostDetail(UUID currentUserId, UUID postId) {
        PostDetailResult cached = postDetailCache.get(postId);
        if (cached != null) {
            return applyViewerOverlay(currentUserId, cached);
        }
        PostDetailResult loaded = loadPostDetailShell(postId);
        postDetailCache.put(postId, loaded);
        return applyViewerOverlay(currentUserId, loaded);
    }

    private PostDetailResult loadPostDetailShell(UUID postId) {
        DiscussPost post = postContentPort.getById(postId);
        List<PostContentBlock> blocks = postContentBlockRepository.listByPostId(postId);
        List<String> tags = tagContentPort.getTagsByPostIds(List.of(postId)).getOrDefault(postId, List.of());
        long likeCount = likeQueryService.countPostLikes(postId);
        List<UUID> assetIds = blocks.stream()
                .map(PostContentBlock::mediaAssetId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        return postDetailAssembler.assemble(post, blocks, postMediaAssetRepository.listByIds(assetIds), tags, likeCount, false, false);
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
        return comments.stream()
                .map(this::toRecentComment)
                .filter(view -> view != null)
                .toList();
    }

    public PostScanResult scanPosts(UUID afterId, int limit) {
        int safeLimit = limit <= 0 ? 500 : Math.min(1000, Math.max(1, limit));
        List<DiscussPost> posts = postContentPort.scanAfterId(afterId, safeLimit);
        List<PostScanResult.PostProjectionResult> items = toPostProjectionResults(posts);
        UUID nextAfterId = posts.isEmpty() ? afterId : posts.get(posts.size() - 1).getId();
        return new PostScanResult(items, nextAfterId, posts.size() == safeLimit);
    }

    public PostScanResult.PostProjectionResult getPostProjectionAllowDeleted(UUID postId) {
        if (postId == null) {
            return null;
        }
        DiscussPost post = postContentPort.getByIdAllowDeleted(postId);
        return toPostProjectionResult(post);
    }

    private List<PostSummaryResult> assembleSummaries(List<DiscussPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<UUID, Comment> lastActivities = commentContentPort.getLatestPostActivitiesByPostIds(postIds);
        Map<UUID, List<String>> tagsByPostId = tagContentPort.getTagsByPostIds(postIds);
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

    private List<PostScanResult.PostProjectionResult> toPostProjectionResults(List<DiscussPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<UUID, List<String>> tagsByPostId = tagContentPort.getTagsByPostIds(postIds);
        Map<UUID, List<PostContentBlock>> blocksByPostId = postContentBlockRepository.listByPostIds(postIds);
        Map<UUID, List<String>> safeTagsByPostId = tagsByPostId == null ? Map.of() : tagsByPostId;
        Map<UUID, List<PostContentBlock>> safeBlocksByPostId = blocksByPostId == null ? Map.of() : blocksByPostId;
        return posts.stream()
                .map(post -> toPostProjectionResult(
                        post,
                        safeTagsByPostId.getOrDefault(post.getId(), List.of()),
                        safeBlocksByPostId.getOrDefault(post.getId(), List.of())
                ))
                .toList();
    }

    private PostScanResult.PostProjectionResult toPostProjectionResult(DiscussPost post) {
        List<String> tags = tagContentPort.getTagsByPostIds(List.of(post.getId())).getOrDefault(post.getId(), List.of());
        List<PostContentBlock> blocks = postContentBlockRepository.listByPostId(post.getId());
        return toPostProjectionResult(post, tags, blocks);
    }

    private PostScanResult.PostProjectionResult toPostProjectionResult(
            DiscussPost post,
            List<String> tags,
            List<PostContentBlock> blocks
    ) {
        return new PostScanResult.PostProjectionResult(
                post.getId(),
                post.getUserId(),
                post.getCategoryId(),
                tags,
                textCodec.decodeOnRead(post.getTitle()),
                textCodec.decodeOnRead(postContentBlockTextProjector.fullText(blocks)),
                post.getType(),
                post.getStatus(),
                post.getCreateTime() == null ? null : post.getCreateTime().toInstant(),
                post.getScore()
        );
    }

    private RecentUserCommentResult toRecentComment(Comment comment) {
        if (comment == null || comment.getId() == null) {
            return null;
        }
        try {
            UUID postId;
            if (comment.getEntityType() == CommentContentRepository.ENTITY_TYPE_POST) {
                postId = comment.getEntityId();
            } else if (comment.getEntityType() == CommentContentRepository.ENTITY_TYPE_COMMENT) {
                Comment parent = commentContentPort.getById(comment.getEntityId());
                if (parent.getEntityType() != CommentContentRepository.ENTITY_TYPE_POST || parent.getEntityId() == null) {
                    return null;
                }
                postId = parent.getEntityId();
            } else {
                return null;
            }
            DiscussPost post = postContentPort.getById(postId);
            return recentUserCommentAssembler.assemble(comment, postId, post.getTitle());
        } catch (BusinessException ex) {
            return null;
        }
    }
}
