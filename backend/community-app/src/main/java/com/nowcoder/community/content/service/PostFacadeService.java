package com.nowcoder.community.content.service;

import com.nowcoder.community.content.dto.CommentResponse;
import com.nowcoder.community.content.dto.CreateCommentRequest;
import com.nowcoder.community.content.dto.CreatePostRequest;
import com.nowcoder.community.content.dto.CreatePostResponse;
import com.nowcoder.community.content.dto.PostDetailResponse;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import com.nowcoder.community.content.dto.UserRecentCommentResponse;
import com.nowcoder.community.content.dto.UpdateCommentRequest;
import com.nowcoder.community.content.dto.UpdatePostRequest;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.nowcoder.community.common.exception.CommonErrorCode.UNAUTHORIZED;

@Service
public class PostFacadeService {

    private static final Logger log = LoggerFactory.getLogger(PostFacadeService.class);

    private final PostService postService;
    private final CommentService commentService;
    private final SensitiveFilter sensitiveFilter;
    private final LikeQueryService likeQueryService;
    private final PostCommandService postCommandService;
    private final TagService tagService;
    private final BookmarkService bookmarkService;
    private final SubscriptionService subscriptionService;
    private final IdempotencyGuard idempotencyGuard;
    private final ContentTextCodec textCodec;

    public PostFacadeService(
            PostService postService,
            CommentService commentService,
            SensitiveFilter sensitiveFilter,
            LikeQueryService likeQueryService,
            PostCommandService postCommandService,
            TagService tagService,
            BookmarkService bookmarkService,
            SubscriptionService subscriptionService,
            IdempotencyGuard idempotencyGuard,
            ContentTextCodec textCodec
    ) {
        this.postService = postService;
        this.commentService = commentService;
        this.sensitiveFilter = sensitiveFilter;
        this.likeQueryService = likeQueryService;
        this.postCommandService = postCommandService;
        this.tagService = tagService;
        this.bookmarkService = bookmarkService;
        this.subscriptionService = subscriptionService;
        this.idempotencyGuard = idempotencyGuard;
        this.textCodec = textCodec;
    }

    public List<PostSummaryResponse> list(Integer currentUserId, String order, Integer categoryId, String tag, Boolean subscribed, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        int orderMode = "hot".equalsIgnoreCase(order) ? PostService.ORDER_HOT : PostService.ORDER_LATEST;

        List<DiscussPost> posts;
        if (Boolean.TRUE.equals(subscribed)) {
            int userId = currentUserId == null ? 0 : currentUserId;
            if (userId <= 0) {
                throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
            }
            List<Integer> subscribedCategoryIds = subscriptionService.listSubscribedCategoryIds(userId);
            posts = postService.listSubscribedPosts(userId, subscribedCategoryIds, p, s, orderMode, categoryId, tag);
        } else {
            posts = postService.listPosts(p, s, orderMode, categoryId, tag);
        }

        return assembleSummaries(posts);
    }

    public List<PostSummaryResponse> listPostsByUser(int userId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 3 : size;
        List<DiscussPost> posts = postService.listPostsByUser(userId, p, s);
        return assembleSummaries(posts);
    }

    public List<PostSummaryResponse> listPostsByIds(List<Integer> postIds) {
        List<DiscussPost> posts = postService.listPostsByIds(postIds);
        return assembleSummaries(posts);
    }

    public List<UserRecentCommentResponse> listRecentCommentsByUser(int userId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 3 : size;
        List<Comment> comments = commentService.listRecentCommentsByUser(userId, p, s);
        return comments.stream()
                .map(this::toRecentComment)
                .filter(value -> value != null)
                .collect(Collectors.toList());
    }

    public CreatePostResponse create(int userId, String idempotencyKey, CreatePostRequest request) {
        return idempotencyGuard.executeRequired("content:create_post", userId, idempotencyKey, CreatePostResponse.class, () -> {
            String title = textCodec.escapeOnWrite(request.getTitle().trim());
            String content = textCodec.escapeOnWrite(request.getContent().trim());
            title = sensitiveFilter.filter(title);
            content = sensitiveFilter.filter(content);

            int postId = postCommandService.createPost(userId, title, content, request.getCategoryId(), request.getTags());
            CreatePostResponse response = new CreatePostResponse();
            response.setPostId(postId);
            return response;
        });
    }

    public PostDetailResponse detail(int currentUserId, int postId) {
        DiscussPost post = postService.getById(postId);

        PostDetailResponse response = new PostDetailResponse();
        response.setId(post.getId());
        response.setUserId(post.getUserId());
        response.setTitle(textCodec.decodeOnRead(post.getTitle()));
        response.setContent(textCodec.decodeOnRead(post.getContent()));
        response.setType(post.getType());
        response.setStatus(post.getStatus());
        response.setCreateTime(post.getCreateTime());
        response.setUpdateTime(post.getUpdateTime());
        response.setEditCount(post.getEditCount());
        response.setCommentCount(post.getCommentCount());
        response.setScore(post.getScore());
        response.setCategoryId(post.getCategoryId());
        response.setTags(tagService.getTagsByPostIds(List.of(postId)).getOrDefault(postId, List.of()));
        response.setLikeCount(likeQueryService.countPostLikes(postId));
        response.setLiked(likeQueryService.hasLikedPost(currentUserId, postId));
        response.setBookmarked(currentUserId > 0 && bookmarkService.hasBookmarked(currentUserId, postId));
        return response;
    }

    public List<CommentResponse> comments(int postId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        List<Comment> rows = commentService.listByPost(postId, p, s);
        return rows == null ? List.of() : rows.stream()
                .map(comment -> CommentResponse.from(comment, textCodec::decodeOnRead))
                .filter(value -> value != null)
                .collect(Collectors.toList());
    }

    public Integer addComment(int userId, String idempotencyKey, int postId, CreateCommentRequest request) {
        return idempotencyGuard.executeRequired("content:create_comment", userId, idempotencyKey, Integer.class,
                () -> commentService.addComment(userId, postId, request.getEntityType(), request.getEntityId(), request.getTargetId(), request.getContent()));
    }

    public void updatePost(int userId, int postId, UpdatePostRequest request) {
        String title = textCodec.escapeOnWrite(request.getTitle().trim());
        String content = textCodec.escapeOnWrite(request.getContent().trim());
        title = sensitiveFilter.filter(title);
        content = sensitiveFilter.filter(content);
        postCommandService.updatePost(userId, postId, title, content, request.getCategoryId(), request.getTags());
    }

    public void deleteByAuthor(int userId, int postId) {
        postCommandService.deletePostByAuthor(userId, postId);
        log.info("[audit] action=post_delete_author actorUserId={} postId={}", userId, postId);
    }

    public void updateComment(int userId, int postId, int commentId, UpdateCommentRequest request) {
        commentService.updateComment(userId, postId, commentId, request.getContent());
    }

    public List<CommentResponse> replies(int postId, int commentId, Integer page, Integer size) {
        commentService.assertCommentBelongsToPost(postId, commentId);
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        List<Comment> rows = commentService.listReplies(commentId, p, s);
        return rows == null ? List.of() : rows.stream()
                .map(comment -> CommentResponse.from(comment, textCodec::decodeOnRead))
                .filter(value -> value != null)
                .collect(Collectors.toList());
    }

    public void top(int actorUserId, int postId) {
        postCommandService.topPost(actorUserId, postId);
        log.info("[audit] action=post_top actorUserId={} postId={}", actorUserId, postId);
    }

    public void wonderful(int actorUserId, int postId) {
        postCommandService.markWonderful(actorUserId, postId);
        log.info("[audit] action=post_wonderful actorUserId={} postId={}", actorUserId, postId);
    }

    public void delete(int actorUserId, int postId) {
        postCommandService.adminDelete(actorUserId, postId);
        log.info("[audit] action=post_delete actorUserId={} postId={}", actorUserId, postId);
    }

    private List<PostSummaryResponse> assembleSummaries(List<DiscussPost> posts) {
        List<Integer> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<Integer, Comment> lastActivities = commentService.getLatestPostActivitiesByPostIds(postIds);
        Map<Integer, List<String>> tagsByPostId = tagService.getTagsByPostIds(postIds);

        return posts.stream()
                .map(post -> toSummary(post, lastActivities.get(post.getId()), tagsByPostId.get(post.getId())))
                .collect(Collectors.toList());
    }

    private UserRecentCommentResponse toRecentComment(Comment comment) {
        if (comment == null || comment.getId() <= 0) {
            return null;
        }

        try {
            int postId;
            if (comment.getEntityType() == CommentService.ENTITY_TYPE_POST) {
                postId = comment.getEntityId();
            } else if (comment.getEntityType() == CommentService.ENTITY_TYPE_COMMENT) {
                Comment parent = commentService.getById(comment.getEntityId());
                if (parent.getEntityType() != CommentService.ENTITY_TYPE_POST || parent.getEntityId() <= 0) {
                    return null;
                }
                postId = parent.getEntityId();
            } else {
                return null;
            }

            DiscussPost post = postService.getById(postId);
            return UserRecentCommentResponse.from(comment, postId, textCodec.decodeOnRead(post.getTitle()), textCodec::decodeOnRead);
        } catch (BusinessException ex) {
            return null;
        }
    }

    private PostSummaryResponse toSummary(DiscussPost post, Comment lastActivity, List<String> tags) {
        PostSummaryResponse response = new PostSummaryResponse();
        response.setId(post.getId());
        response.setUserId(post.getUserId());
        response.setCategoryId(post.getCategoryId());
        response.setTags(tags == null ? List.of() : tags);
        response.setTitle(textCodec.decodeOnRead(post.getTitle()));
        response.setType(post.getType());
        response.setStatus(post.getStatus());
        response.setCreateTime(post.getCreateTime());
        response.setCommentCount(post.getCommentCount());
        response.setScore(post.getScore());

        if (lastActivity != null && lastActivity.getUserId() > 0 && lastActivity.getCreateTime() != null) {
            response.setLastReplyUserId(lastActivity.getUserId());
            response.setLastReplyTime(lastActivity.getCreateTime());
            response.setLastReplyPreview(textCodec.decodeOnRead(lastActivity.getContent()));
        }

        if (response.getLastReplyTime() != null) {
            if (response.getCreateTime() == null || response.getLastReplyTime().after(response.getCreateTime())) {
                response.setLastActivityTime(response.getLastReplyTime());
            } else {
                response.setLastActivityTime(response.getCreateTime());
            }
        } else {
            response.setLastActivityTime(response.getCreateTime());
        }
        return response;
    }
}
