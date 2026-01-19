package com.nowcoder.community.content.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.content.api.dto.CreateCommentRequest;
import com.nowcoder.community.content.api.dto.CreatePostRequest;
import com.nowcoder.community.content.api.dto.CreatePostResponse;
import com.nowcoder.community.content.api.dto.PostDetailResponse;
import com.nowcoder.community.content.api.dto.PostSummaryResponse;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.content.service.CommentService;
import com.nowcoder.community.content.service.PostCommandService;
import com.nowcoder.community.content.service.PostService;
import com.nowcoder.community.content.util.SensitiveFilter;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private static final int ENTITY_TYPE_POST = 1;

    private static final Logger log = LoggerFactory.getLogger(PostController.class);

    private final PostService postService;
    private final CommentService commentService;
    private final SensitiveFilter sensitiveFilter;
    private final LikeQueryService likeQueryService;
    private final PostScoreQueue postScoreQueue;
    private final ContentEventPublisher eventPublisher;
    private final PostCommandService postCommandService;

    public PostController(
            PostService postService,
            CommentService commentService,
            SensitiveFilter sensitiveFilter,
            LikeQueryService likeQueryService,
            PostScoreQueue postScoreQueue,
            ContentEventPublisher eventPublisher,
            PostCommandService postCommandService
    ) {
        this.postService = postService;
        this.commentService = commentService;
        this.sensitiveFilter = sensitiveFilter;
        this.likeQueryService = likeQueryService;
        this.postScoreQueue = postScoreQueue;
        this.eventPublisher = eventPublisher;
        this.postCommandService = postCommandService;
    }

    @GetMapping
    public Result<List<PostSummaryResponse>> list(
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        int orderMode = "hot".equalsIgnoreCase(order) ? PostService.ORDER_HOT : PostService.ORDER_LATEST;
        List<PostSummaryResponse> items = postService.listPosts(p, s, orderMode).stream().map(this::toSummary).collect(Collectors.toList());
        return Result.ok(items);
    }

    @PostMapping
    public Result<CreatePostResponse> create(Authentication authentication, @Valid @RequestBody CreatePostRequest request) {
        int userId = currentUserId(authentication);

        String title = HtmlUtils.htmlEscape(request.getTitle().trim());
        String content = HtmlUtils.htmlEscape(request.getContent().trim());
        title = sensitiveFilter.filter(title);
        content = sensitiveFilter.filter(content);

        int postId = postCommandService.createPost(userId, title, content);

        CreatePostResponse resp = new CreatePostResponse();
        resp.setPostId(postId);
        return Result.ok(resp);
    }

    @GetMapping("/{postId}")
    public Result<PostDetailResponse> detail(Authentication authentication, @PathVariable int postId) {
        DiscussPost post = postService.getById(postId);

        int currentUserId = 0;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                currentUserId = Integer.parseInt(jwt.getSubject());
            } catch (Exception ignored) {
            }
        }

        PostDetailResponse resp = new PostDetailResponse();
        resp.setId(post.getId());
        resp.setUserId(post.getUserId());
        resp.setTitle(post.getTitle());
        resp.setContent(post.getContent());
        resp.setType(post.getType());
        resp.setStatus(post.getStatus());
        resp.setCreateTime(post.getCreateTime());
        resp.setCommentCount(post.getCommentCount());
        resp.setScore(post.getScore());
        resp.setLikeCount(likeQueryService.countPostLikes(postId));
        resp.setLiked(likeQueryService.hasLikedPost(currentUserId, postId));
        return Result.ok(resp);
    }

    @GetMapping("/{postId}/comments")
    public Result<List<Comment>> comments(
            @PathVariable int postId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        return Result.ok(commentService.listByPost(postId, p, s));
    }

    @PostMapping("/{postId}/comments")
    public Result<Integer> addComment(Authentication authentication, @PathVariable int postId, @Valid @RequestBody CreateCommentRequest request) {
        int userId = currentUserId(authentication);
        int id = commentService.addComment(userId, postId, request.getEntityType(), request.getEntityId(), request.getTargetId(), request.getContent());
        return Result.ok(id);
    }

    @GetMapping("/{postId}/comments/{commentId}/replies")
    public Result<List<Comment>> replies(
            @PathVariable int postId,
            @PathVariable int commentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        // postId 仅用于路径对齐与基本校验（避免跨帖乱查）
        postService.getById(postId);
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        return Result.ok(commentService.listReplies(commentId, p, s));
    }

    // 置顶（type=1）
    @PostMapping("/{postId}/top")
    public Result<Void> top(Authentication authentication, @PathVariable int postId) {
        int actorUserId = currentUserId(authentication);
        postService.updateType(postId, 1);
        DiscussPost post = postService.getById(postId);

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(post.getUserId());
        payload.setTitle(post.getTitle());
        payload.setContent(post.getContent());
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setScore(post.getScore());
        payload.setCreateTime(post.getCreateTime() == null ? null : post.getCreateTime().toInstant());
        eventPublisher.publishPostUpdated(payload);

        log.info("[audit] action=post_top actorUserId={} postId={}", actorUserId, postId);
        return Result.ok();
    }

    // 加精（status=1）
    @PostMapping("/{postId}/wonderful")
    public Result<Void> wonderful(Authentication authentication, @PathVariable int postId) {
        int actorUserId = currentUserId(authentication);
        postService.updateStatus(postId, 1);
        postScoreQueue.add(postId);
        DiscussPost post = postService.getById(postId);

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(post.getUserId());
        payload.setTitle(post.getTitle());
        payload.setContent(post.getContent());
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setScore(post.getScore());
        payload.setCreateTime(post.getCreateTime() == null ? null : post.getCreateTime().toInstant());
        eventPublisher.publishPostUpdated(payload);

        log.info("[audit] action=post_wonderful actorUserId={} postId={}", actorUserId, postId);
        return Result.ok();
    }

    // 删除（status=2）
    @PostMapping("/{postId}/delete")
    public Result<Void> delete(Authentication authentication, @PathVariable int postId) {
        int actorUserId = currentUserId(authentication);
        postService.updateStatus(postId, 2);
        DiscussPost post = postService.getById(postId);

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(post.getUserId());
        payload.setTitle(post.getTitle());
        payload.setContent(post.getContent());
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setScore(post.getScore());
        payload.setCreateTime(Instant.now());
        eventPublisher.publishPostDeleted(payload);

        log.info("[audit] action=post_delete actorUserId={} postId={}", actorUserId, postId);
        return Result.ok();
    }

    private PostSummaryResponse toSummary(DiscussPost post) {
        PostSummaryResponse r = new PostSummaryResponse();
        r.setId(post.getId());
        r.setUserId(post.getUserId());
        r.setTitle(post.getTitle());
        r.setType(post.getType());
        r.setStatus(post.getStatus());
        r.setCreateTime(post.getCreateTime());
        r.setCommentCount(post.getCommentCount());
        r.setScore(post.getScore());
        return r;
    }

    private int currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "未获取到认证信息");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String sub = jwt.getSubject();
        try {
            return Integer.parseInt(sub);
        } catch (Exception e) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
    }
}
