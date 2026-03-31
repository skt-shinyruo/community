package com.nowcoder.community.content.controller;

import com.nowcoder.community.content.api.action.CommentActionApi;
import com.nowcoder.community.content.api.action.PostModerationActionApi;
import com.nowcoder.community.content.api.action.PostPublishingActionApi;
import com.nowcoder.community.content.api.model.CommentView;
import com.nowcoder.community.content.api.model.PostCreateResult;
import com.nowcoder.community.content.api.model.PostDetailView;
import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.query.CommentReadQueryApi;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.content.dto.CommentResponse;
import com.nowcoder.community.content.dto.BatchPostSummaryRequest;
import com.nowcoder.community.content.dto.CreateCommentRequest;
import com.nowcoder.community.content.dto.CreatePostRequest;
import com.nowcoder.community.content.dto.CreatePostResponse;
import com.nowcoder.community.content.dto.PostDetailResponse;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import com.nowcoder.community.content.dto.UpdateCommentRequest;
import com.nowcoder.community.content.dto.UpdatePostRequest;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
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

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostReadQueryApi postReadQueryApi;
    private final CommentReadQueryApi commentReadQueryApi;
    private final PostPublishingActionApi postPublishingActionApi;
    private final PostModerationActionApi postModerationActionApi;
    private final CommentActionApi commentActionApi;

    public PostController(
            PostReadQueryApi postReadQueryApi,
            CommentReadQueryApi commentReadQueryApi,
            PostPublishingActionApi postPublishingActionApi,
            PostModerationActionApi postModerationActionApi,
            CommentActionApi commentActionApi
    ) {
        this.postReadQueryApi = postReadQueryApi;
        this.commentReadQueryApi = commentReadQueryApi;
        this.postPublishingActionApi = postPublishingActionApi;
        this.postModerationActionApi = postModerationActionApi;
        this.commentActionApi = commentActionApi;
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
        Integer maybeCurrentUserId = CurrentUser.tryUserId(authentication);
        int currentUserId = maybeCurrentUserId == null ? 0 : maybeCurrentUserId;
        return Result.ok(postReadQueryApi.listPosts(currentUserId, order, categoryId, tag, subscribed, page, size).stream()
                .map(this::toPostSummaryResponse)
                .toList());
    }

    @PostMapping
    public Result<CreatePostResponse> create(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePostRequest request
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        PostCreateResult result = postPublishingActionApi.create(
                userId,
                idempotencyKey,
                request.getTitle(),
                request.getContent(),
                request.getCategoryId(),
                request.getTags()
        );
        return Result.ok(toCreatePostResponse(result));
    }

    @PostMapping("/batch-summary")
    public Result<List<PostSummaryResponse>> batchSummary(@Valid @RequestBody BatchPostSummaryRequest request) {
        List<Integer> postIds = request == null ? List.of() : request.getPostIds();
        return Result.ok(postReadQueryApi.listPostsByIds(postIds).stream()
                .map(this::toPostSummaryResponse)
                .toList());
    }

    @GetMapping("/{postId}")
    public Result<PostDetailResponse> detail(Authentication authentication, @PathVariable int postId) {
        Integer maybeCurrentUserId = CurrentUser.tryUserId(authentication);
        int currentUserId = maybeCurrentUserId == null ? 0 : maybeCurrentUserId;
        return Result.ok(toPostDetailResponse(postReadQueryApi.getPostDetail(currentUserId, postId)));
    }

    @GetMapping("/{postId}/comments")
    public Result<List<CommentResponse>> comments(
            @PathVariable int postId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(commentReadQueryApi.comments(postId, page, size).stream()
                .map(this::toCommentResponse)
                .toList());
    }

    @PostMapping("/{postId}/comments")
    public Result<Integer> addComment(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable int postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(commentActionApi.addComment(
                userId,
                idempotencyKey,
                postId,
                request.getEntityType(),
                request.getEntityId(),
                request.getTargetId(),
                request.getContent()
        ));
    }

    @PutMapping("/{postId}")
    public Result<Void> updatePost(Authentication authentication, @PathVariable int postId, @Valid @RequestBody UpdatePostRequest request) {
        int userId = CurrentUser.requireUserId(authentication);
        postPublishingActionApi.updatePost(userId, postId, request.getTitle(), request.getContent(), request.getCategoryId(), request.getTags());
        return Result.ok();
    }

    @DeleteMapping("/{postId}")
    public Result<Void> deleteByAuthor(Authentication authentication, @PathVariable int postId) {
        int userId = CurrentUser.requireUserId(authentication);
        postPublishingActionApi.deleteByAuthor(userId, postId);
        return Result.ok();
    }

    @PutMapping("/{postId}/comments/{commentId}")
    public Result<Void> updateComment(
            Authentication authentication,
            @PathVariable int postId,
            @PathVariable int commentId,
            @Valid @RequestBody UpdateCommentRequest request
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        commentActionApi.updateComment(userId, postId, commentId, request.getContent());
        return Result.ok();
    }

    @GetMapping("/{postId}/comments/{commentId}/replies")
    public Result<List<CommentResponse>> replies(
            @PathVariable int postId,
            @PathVariable int commentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(commentReadQueryApi.replies(postId, commentId, page, size).stream()
                .map(this::toCommentResponse)
                .toList());
    }

    @PostMapping("/{postId}/top")
    public Result<Void> top(Authentication authentication, @PathVariable int postId) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        postModerationActionApi.top(actorUserId, postId);
        return Result.ok();
    }

    @PostMapping("/{postId}/wonderful")
    public Result<Void> wonderful(Authentication authentication, @PathVariable int postId) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        postModerationActionApi.wonderful(actorUserId, postId);
        return Result.ok();
    }

    @PostMapping("/{postId}/delete")
    public Result<Void> delete(Authentication authentication, @PathVariable int postId) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        postModerationActionApi.delete(actorUserId, postId);
        return Result.ok();
    }

    private CreatePostResponse toCreatePostResponse(PostCreateResult result) {
        CreatePostResponse response = new CreatePostResponse();
        response.setPostId(result.postId());
        return response;
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

    private CommentResponse toCommentResponse(CommentView view) {
        CommentResponse response = new CommentResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setEntityType(view.entityType());
        response.setEntityId(view.entityId());
        response.setTargetId(view.targetId());
        response.setContent(view.content());
        response.setCreateTime(view.createTime());
        response.setUpdateTime(view.updateTime());
        response.setEditCount(view.editCount());
        return response;
    }
}
