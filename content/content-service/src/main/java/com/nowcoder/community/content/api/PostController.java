package com.nowcoder.community.content.api;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.platform.idempotency.IdempotencyGuard;
import com.nowcoder.community.content.api.dto.CommentResponse;
import com.nowcoder.community.content.api.dto.CreateCommentRequest;
import com.nowcoder.community.content.api.dto.CreatePostRequest;
import com.nowcoder.community.content.api.dto.CreatePostResponse;
import com.nowcoder.community.content.api.dto.PostDetailResponse;
import com.nowcoder.community.content.api.dto.PostSummaryResponse;
import com.nowcoder.community.content.api.dto.UpdateCommentRequest;
import com.nowcoder.community.content.api.dto.UpdatePostRequest;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.service.BookmarkService;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.service.CommentService;
import com.nowcoder.community.content.service.PostCommandService;
import com.nowcoder.community.content.service.PostService;
import com.nowcoder.community.content.service.SubscriptionService;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.contracts.api.CommonErrorCode.UNAUTHORIZED;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private static final int ENTITY_TYPE_POST = EntityTypes.POST;

    private static final Logger log = LoggerFactory.getLogger(PostController.class);

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

    public PostController(
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

    @GetMapping
    public Result<List<PostSummaryResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean subscribed,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        int orderMode = "hot".equalsIgnoreCase(order) ? PostService.ORDER_HOT : PostService.ORDER_LATEST;

        List<DiscussPost> posts;
        if (Boolean.TRUE.equals(subscribed)) {
            int userId = currentUserId(authentication);
            List<Integer> subscribedCategoryIds = subscriptionService.listSubscribedCategoryIds(userId);
            posts = postService.listSubscribedPosts(userId, subscribedCategoryIds, p, s, orderMode, categoryId, tag);
        } else {
            posts = postService.listPosts(p, s, orderMode, categoryId, tag);
        }

        List<Integer> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<Integer, Comment> lastActivities = commentService.getLatestPostActivitiesByPostIds(postIds);
        Map<Integer, List<String>> tagsByPostId = tagService.getTagsByPostIds(postIds);

        List<PostSummaryResponse> items = posts.stream()
                .map(post -> toSummary(post, lastActivities.get(post.getId()), tagsByPostId.get(post.getId())))
                .collect(Collectors.toList());
        return Result.ok(items);
    }

    @PostMapping
    public Result<CreatePostResponse> create(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePostRequest request
    ) {
        int userId = currentUserId(authentication);

        return Result.ok(idempotencyGuard.executeRequired("create_post", userId, idempotencyKey, CreatePostResponse.class, () -> {
            String title = textCodec.escapeOnWrite(request.getTitle().trim());
            String content = textCodec.escapeOnWrite(request.getContent().trim());
            title = sensitiveFilter.filter(title);
            content = sensitiveFilter.filter(content);

            int postId = postCommandService.createPost(userId, title, content, request.getCategoryId(), request.getTags());

            CreatePostResponse resp = new CreatePostResponse();
            resp.setPostId(postId);
            return resp;
        }));
    }

    @GetMapping("/{postId}")
    public Result<PostDetailResponse> detail(Authentication authentication, @PathVariable int postId) {
        DiscussPost post = postService.getById(postId);

        int currentUserId = 0;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                currentUserId = Integer.parseInt(jwt.getSubject());
            } catch (NumberFormatException ignored) {
            }
        }

        PostDetailResponse resp = new PostDetailResponse();
        resp.setId(post.getId());
        resp.setUserId(post.getUserId());
        resp.setTitle(textCodec.decodeOnRead(post.getTitle()));
        resp.setContent(textCodec.decodeOnRead(post.getContent()));
        resp.setType(post.getType());
        resp.setStatus(post.getStatus());
        resp.setCreateTime(post.getCreateTime());
        resp.setUpdateTime(post.getUpdateTime());
        resp.setEditCount(post.getEditCount());
        resp.setCommentCount(post.getCommentCount());
        resp.setScore(post.getScore());
        resp.setCategoryId(post.getCategoryId());
        resp.setTags(tagService.getTagsByPostIds(List.of(postId)).getOrDefault(postId, List.of()));
        resp.setLikeCount(likeQueryService.countPostLikes(postId));
        resp.setLiked(likeQueryService.hasLikedPost(currentUserId, postId));
        if (currentUserId > 0) {
            resp.setBookmarked(bookmarkService.hasBookmarked(currentUserId, postId));
        } else {
            resp.setBookmarked(false);
        }
        return Result.ok(resp);
    }

    @GetMapping("/{postId}/comments")
    public Result<List<CommentResponse>> comments(
            @PathVariable int postId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        List<Comment> rows = commentService.listByPost(postId, p, s);
        List<CommentResponse> resp = (rows == null ? List.<CommentResponse>of() : rows.stream()
                .map(c -> CommentResponse.from(c, textCodec::decodeOnRead))
                .filter(r -> r != null)
                .collect(Collectors.toList()));
        return Result.ok(resp);
    }

    @PostMapping("/{postId}/comments")
    public Result<Integer> addComment(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable int postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        int userId = currentUserId(authentication);
        Integer id = idempotencyGuard.executeRequired("create_comment", userId, idempotencyKey, Integer.class,
                () -> commentService.addComment(userId, postId, request.getEntityType(), request.getEntityId(), request.getTargetId(), request.getContent()));
        return Result.ok(id);
    }

    @PutMapping("/{postId}")
    public Result<Void> updatePost(Authentication authentication, @PathVariable int postId, @Valid @RequestBody UpdatePostRequest request) {
        int userId = currentUserId(authentication);

        String title = textCodec.escapeOnWrite(request.getTitle().trim());
        String content = textCodec.escapeOnWrite(request.getContent().trim());
        title = sensitiveFilter.filter(title);
        content = sensitiveFilter.filter(content);

        postCommandService.updatePost(userId, postId, title, content, request.getCategoryId(), request.getTags());
        return Result.ok();
    }

    @DeleteMapping("/{postId}")
    public Result<Void> deleteByAuthor(Authentication authentication, @PathVariable int postId) {
        int userId = currentUserId(authentication);
        postCommandService.deletePostByAuthor(userId, postId);
        log.info("[audit] action=post_delete_author actorUserId={} postId={}", userId, postId);
        return Result.ok();
    }

    @PutMapping("/{postId}/comments/{commentId}")
    public Result<Void> updateComment(
            Authentication authentication,
            @PathVariable int postId,
            @PathVariable int commentId,
            @Valid @RequestBody UpdateCommentRequest request
    ) {
        int userId = currentUserId(authentication);
        commentService.updateComment(userId, postId, commentId, request.getContent());
        return Result.ok();
    }

    @GetMapping("/{postId}/comments/{commentId}/replies")
    public Result<List<CommentResponse>> replies(
            @PathVariable int postId,
            @PathVariable int commentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        // 路径语义校验：避免 replies 跨帖枚举
        commentService.assertCommentBelongsToPost(postId, commentId);
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        List<Comment> rows = commentService.listReplies(commentId, p, s);
        List<CommentResponse> resp = (rows == null ? List.<CommentResponse>of() : rows.stream()
                .map(c -> CommentResponse.from(c, textCodec::decodeOnRead))
                .filter(r -> r != null)
                .collect(Collectors.toList()));
        return Result.ok(resp);
    }

    // 置顶（type=1）
    @PostMapping("/{postId}/top")
    public Result<Void> top(Authentication authentication, @PathVariable int postId) {
        int actorUserId = currentUserId(authentication);
        postCommandService.topPost(actorUserId, postId);

        log.info("[audit] action=post_top actorUserId={} postId={}", actorUserId, postId);
        return Result.ok();
    }

    // 加精（status=1）
    @PostMapping("/{postId}/wonderful")
    public Result<Void> wonderful(Authentication authentication, @PathVariable int postId) {
        int actorUserId = currentUserId(authentication);
        postCommandService.markWonderful(actorUserId, postId);

        log.info("[audit] action=post_wonderful actorUserId={} postId={}", actorUserId, postId);
        return Result.ok();
    }

    // 删除（status=2）
    @PostMapping("/{postId}/delete")
    public Result<Void> delete(Authentication authentication, @PathVariable int postId) {
        int actorUserId = currentUserId(authentication);
        postCommandService.adminDelete(actorUserId, postId);

        log.info("[audit] action=post_delete actorUserId={} postId={}", actorUserId, postId);
        return Result.ok();
    }

    private PostSummaryResponse toSummary(DiscussPost post, Comment lastActivity, List<String> tags) {
        PostSummaryResponse r = new PostSummaryResponse();
        r.setId(post.getId());
        r.setUserId(post.getUserId());
        r.setCategoryId(post.getCategoryId());
        r.setTags(tags == null ? List.of() : tags);
        r.setTitle(textCodec.decodeOnRead(post.getTitle()));
        r.setType(post.getType());
        r.setStatus(post.getStatus());
        r.setCreateTime(post.getCreateTime());
        r.setCommentCount(post.getCommentCount());
        r.setScore(post.getScore());

        if (lastActivity != null && lastActivity.getUserId() > 0 && lastActivity.getCreateTime() != null) {
            r.setLastReplyUserId(lastActivity.getUserId());
            r.setLastReplyTime(lastActivity.getCreateTime());
        }

        // lastActivityTime：用于列表的“活动”列与未读判断（优先最后回复时间，否则 fallback 到发帖时间）。
        if (r.getLastReplyTime() != null) {
            if (r.getCreateTime() == null || r.getLastReplyTime().after(r.getCreateTime())) {
                r.setLastActivityTime(r.getLastReplyTime());
            } else {
                r.setLastActivityTime(r.getCreateTime());
            }
        } else {
            r.setLastActivityTime(r.getCreateTime());
        }
        return r;
    }

    private int currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String sub = jwt.getSubject();
        try {
            return Integer.parseInt(sub);
        } catch (NumberFormatException e) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
    }
}
