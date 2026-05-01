package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.SubscriptionRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.content.application.PostDetailAssembler;
import com.nowcoder.community.content.application.PostSummaryAssembler;
import com.nowcoder.community.content.application.RecentUserCommentAssembler;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.application.result.RecentUserCommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.application.LikeQueryPort;
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
        DiscussPost post = postContentPort.getById(postId);
        List<String> tags = tagContentPort.getTagsByPostIds(List.of(postId)).getOrDefault(postId, List.of());
        long likeCount = likeQueryService.countPostLikes(postId);
        boolean liked = likeQueryService.hasLikedPost(currentUserId, postId);
        boolean bookmarked = currentUserId != null && bookmarkContentPort.hasBookmarked(currentUserId, postId);
        return postDetailAssembler.assemble(post, tags, likeCount, liked, bookmarked);
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

    private List<PostSummaryResult> assembleSummaries(List<DiscussPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<UUID, Comment> lastActivities = commentContentPort.getLatestPostActivitiesByPostIds(postIds);
        Map<UUID, List<String>> tagsByPostId = tagContentPort.getTagsByPostIds(postIds);
        return posts.stream()
                .map(post -> postSummaryAssembler.assemble(post, lastActivities.get(post.getId()), tagsByPostId.get(post.getId())))
                .toList();
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
