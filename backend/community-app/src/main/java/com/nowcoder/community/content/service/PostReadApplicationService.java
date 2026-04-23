package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.model.PostDetailView;
import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.content.assembler.PostDetailAssembler;
import com.nowcoder.community.content.assembler.PostSummaryAssembler;
import com.nowcoder.community.content.assembler.RecentUserCommentAssembler;
import com.nowcoder.community.content.dto.PostDetailResponse;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.like.LikeQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.UNAUTHORIZED;

@Service
public class PostReadApplicationService implements PostReadQueryApi {

    private final PostService postService;
    private final CommentService commentService;
    private final LikeQueryService likeQueryService;
    private final TagService tagService;
    private final BookmarkService bookmarkService;
    private final SubscriptionService subscriptionService;
    private final PostSummaryAssembler postSummaryAssembler;
    private final PostDetailAssembler postDetailAssembler;
    private final RecentUserCommentAssembler recentUserCommentAssembler;

    public PostReadApplicationService(
            PostService postService,
            CommentService commentService,
            LikeQueryService likeQueryService,
            TagService tagService,
            BookmarkService bookmarkService,
            SubscriptionService subscriptionService,
            PostSummaryAssembler postSummaryAssembler,
            PostDetailAssembler postDetailAssembler,
            RecentUserCommentAssembler recentUserCommentAssembler
    ) {
        this.postService = postService;
        this.commentService = commentService;
        this.likeQueryService = likeQueryService;
        this.tagService = tagService;
        this.bookmarkService = bookmarkService;
        this.subscriptionService = subscriptionService;
        this.postSummaryAssembler = postSummaryAssembler;
        this.postDetailAssembler = postDetailAssembler;
        this.recentUserCommentAssembler = recentUserCommentAssembler;
    }

    @Override
    public List<PostSummaryView> listPosts(UUID currentUserId, String order, UUID categoryId, String tag, Boolean subscribed, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        int orderMode = "hot".equalsIgnoreCase(order) ? PostService.ORDER_HOT : PostService.ORDER_LATEST;

        List<DiscussPost> posts;
        if (Boolean.TRUE.equals(subscribed)) {
            if (currentUserId == null) {
                throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
            }
            List<UUID> subscribedCategoryIds = subscriptionService.listSubscribedCategoryIds(currentUserId);
            posts = postService.listSubscribedPosts(currentUserId, subscribedCategoryIds, p, s, orderMode, categoryId, tag);
        } else {
            posts = postService.listPosts(p, s, orderMode, categoryId, tag);
        }

        return assembleSummaries(posts);
    }

    @Override
    public List<PostSummaryView> listPostsByUser(UUID userId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 3 : size;
        return assembleSummaries(postService.listPostsByUser(userId, p, s));
    }

    @Override
    public List<PostSummaryView> listPostsByIds(List<UUID> postIds) {
        return assembleSummaries(postService.listPostsByIds(postIds));
    }

    public List<PostSummaryResponse> listPostSummaryResponses(
            UUID currentUserId,
            String order,
            UUID categoryId,
            String tag,
            Boolean subscribed,
            Integer page,
            Integer size
    ) {
        return listPosts(currentUserId, order, categoryId, tag, subscribed, page, size).stream()
                .map(this::toPostSummaryResponse)
                .toList();
    }

    public List<PostSummaryResponse> listPostSummaryResponsesByIds(List<UUID> postIds) {
        return listPostsByIds(postIds).stream()
                .map(this::toPostSummaryResponse)
                .toList();
    }

    @Override
    public PostDetailView getPostDetail(UUID currentUserId, UUID postId) {
        DiscussPost post = postService.getById(postId);
        List<String> tags = tagService.getTagsByPostIds(List.of(postId)).getOrDefault(postId, List.of());
        long likeCount = likeQueryService.countPostLikes(postId);
        boolean liked = likeQueryService.hasLikedPost(currentUserId, postId);
        boolean bookmarked = currentUserId != null && bookmarkService.hasBookmarked(currentUserId, postId);
        return postDetailAssembler.assemble(post, tags, likeCount, liked, bookmarked);
    }

    public PostDetailResponse getPostDetailResponse(UUID currentUserId, UUID postId) {
        return toPostDetailResponse(getPostDetail(currentUserId, postId));
    }

    @Override
    public List<RecentUserCommentView> listRecentCommentsByUser(UUID userId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 3 : size;
        List<Comment> comments = commentService.listRecentCommentsByUser(userId, p, s);
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        return comments.stream()
                .map(this::toRecentComment)
                .filter(view -> view != null)
                .toList();
    }

    private List<PostSummaryView> assembleSummaries(List<DiscussPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<UUID, Comment> lastActivities = commentService.getLatestPostActivitiesByPostIds(postIds);
        Map<UUID, List<String>> tagsByPostId = tagService.getTagsByPostIds(postIds);
        return posts.stream()
                .map(post -> postSummaryAssembler.assemble(post, lastActivities.get(post.getId()), tagsByPostId.get(post.getId())))
                .toList();
    }

    private RecentUserCommentView toRecentComment(Comment comment) {
        if (comment == null || comment.getId() == null) {
            return null;
        }
        try {
            UUID postId;
            if (comment.getEntityType() == CommentService.ENTITY_TYPE_POST) {
                postId = comment.getEntityId();
            } else if (comment.getEntityType() == CommentService.ENTITY_TYPE_COMMENT) {
                Comment parent = commentService.getById(comment.getEntityId());
                if (parent.getEntityType() != CommentService.ENTITY_TYPE_POST || parent.getEntityId() == null) {
                    return null;
                }
                postId = parent.getEntityId();
            } else {
                return null;
            }
            DiscussPost post = postService.getById(postId);
            return recentUserCommentAssembler.assemble(comment, postId, post.getTitle());
        } catch (BusinessException ex) {
            return null;
        }
    }

    private PostSummaryResponse toPostSummaryResponse(PostSummaryView view) {
        PostSummaryResponse response = new PostSummaryResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setTitle(view.title());
        response.setType(view.type());
        response.setStatus(view.status());
        response.setCreateTime(view.createTime());
        response.setCommentCount(view.commentCount());
        response.setScore(view.score());
        response.setCategoryId(view.categoryId());
        response.setTags(view.tags());
        response.setLastReplyUserId(view.lastReplyUserId());
        response.setLastReplyTime(view.lastReplyTime());
        response.setLastActivityTime(view.lastActivityTime());
        response.setLastReplyPreview(view.lastReplyPreview());
        return response;
    }

    private PostDetailResponse toPostDetailResponse(PostDetailView view) {
        PostDetailResponse response = new PostDetailResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setTitle(view.title());
        response.setContent(view.content());
        response.setType(view.type());
        response.setStatus(view.status());
        response.setCreateTime(view.createTime());
        response.setUpdateTime(view.updateTime());
        response.setEditCount(view.editCount());
        response.setCommentCount(view.commentCount());
        response.setScore(view.score());
        response.setCategoryId(view.categoryId());
        response.setTags(view.tags());
        response.setLikeCount(view.likeCount());
        response.setLiked(view.liked());
        response.setBookmarked(view.bookmarked());
        return response;
    }
}
