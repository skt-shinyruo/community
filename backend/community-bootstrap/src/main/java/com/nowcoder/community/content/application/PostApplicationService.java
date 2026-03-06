package com.nowcoder.community.content.application;

import com.nowcoder.community.content.api.dto.CommentResponse;
import com.nowcoder.community.content.api.dto.CreateCommentRequest;
import com.nowcoder.community.content.api.dto.CreatePostRequest;
import com.nowcoder.community.content.api.dto.CreatePostResponse;
import com.nowcoder.community.content.api.dto.PostDetailResponse;
import com.nowcoder.community.content.api.dto.PostSummaryResponse;
import com.nowcoder.community.content.api.dto.UpdateCommentRequest;
import com.nowcoder.community.content.api.dto.UpdatePostRequest;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.service.BookmarkService;
import com.nowcoder.community.content.service.CommentService;
import com.nowcoder.community.content.service.PostCommandService;
import com.nowcoder.community.content.service.PostService;
import com.nowcoder.community.content.service.SubscriptionService;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.nowcoder.community.contracts.api.CommonErrorCode.UNAUTHORIZED;

@Service
public class PostApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PostApplicationService.class);

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

    public PostApplicationService(
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

        List<Integer> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<Integer, Comment> lastActivities = commentService.getLatestPostActivitiesByPostIds(postIds);
        Map<Integer, List<String>> tagsByPostId = tagService.getTagsByPostIds(postIds);

        return posts.stream()
                .map(post -> toSummary(post, lastActivities.get(post.getId()), tagsByPostId.get(post.getId())))
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
