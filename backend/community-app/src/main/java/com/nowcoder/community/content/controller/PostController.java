package com.nowcoder.community.content.controller;

import com.nowcoder.community.content.dto.CommentResponse;
import com.nowcoder.community.content.dto.BatchPostSummaryRequest;
import com.nowcoder.community.content.dto.CreateCommentRequest;
import com.nowcoder.community.content.dto.CreatePostRequest;
import com.nowcoder.community.content.dto.CreatePostResponse;
import com.nowcoder.community.content.dto.PostDetailResponse;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import com.nowcoder.community.content.dto.UpdateCommentRequest;
import com.nowcoder.community.content.dto.UpdatePostRequest;
import com.nowcoder.community.content.service.CommentApplicationService;
import com.nowcoder.community.content.service.CommentReadApplicationService;
import com.nowcoder.community.content.service.PostModerationApplicationService;
import com.nowcoder.community.content.service.PostPublishingApplicationService;
import com.nowcoder.community.content.service.PostReadApplicationService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostReadApplicationService postReadApplicationService;
    private final CommentReadApplicationService commentReadApplicationService;
    private final PostPublishingApplicationService postPublishingApplicationService;
    private final PostModerationApplicationService postModerationApplicationService;
    private final CommentApplicationService commentApplicationService;

    public PostController(
            PostReadApplicationService postReadApplicationService,
            CommentReadApplicationService commentReadApplicationService,
            PostPublishingApplicationService postPublishingApplicationService,
            PostModerationApplicationService postModerationApplicationService,
            CommentApplicationService commentApplicationService
    ) {
        this.postReadApplicationService = postReadApplicationService;
        this.commentReadApplicationService = commentReadApplicationService;
        this.postPublishingApplicationService = postPublishingApplicationService;
        this.postModerationApplicationService = postModerationApplicationService;
        this.commentApplicationService = commentApplicationService;
    }

    @GetMapping
    public Result<List<PostSummaryResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean subscribed,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        return Result.ok(postReadApplicationService.listPostSummaryResponses(currentUserId, order, categoryId, tag, subscribed, page, size));
    }

    @PostMapping
    public Result<CreatePostResponse> create(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePostRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        UUID postId = postPublishingApplicationService.createPost(
                userId,
                idempotencyKey,
                request.getTitle(),
                request.getContent(),
                request.getCategoryId(),
                request.getTags()
        );
        return Result.ok(toCreatePostResponse(postId));
    }

    @PostMapping("/batch-summary")
    public Result<List<PostSummaryResponse>> batchSummary(@Valid @RequestBody BatchPostSummaryRequest request) {
        List<UUID> postIds = request == null ? List.of() : request.getPostIds();
        return Result.ok(postReadApplicationService.listPostSummaryResponsesByIds(postIds));
    }

    @GetMapping("/{postId}")
    public Result<PostDetailResponse> detail(Authentication authentication, @PathVariable UUID postId) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        return Result.ok(postReadApplicationService.getPostDetailResponse(currentUserId, postId));
    }

    @GetMapping("/{postId}/comments")
    public Result<List<CommentResponse>> comments(
            @PathVariable UUID postId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(commentReadApplicationService.commentResponses(postId, page, size));
    }

    @PostMapping("/{postId}/comments")
    public Result<UUID> addComment(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(commentApplicationService.addComment(
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
    public Result<Void> updatePost(Authentication authentication, @PathVariable UUID postId, @Valid @RequestBody UpdatePostRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        postPublishingApplicationService.updatePost(userId, postId, request.getTitle(), request.getContent(), request.getCategoryId(), request.getTags());
        return Result.ok();
    }

    @DeleteMapping("/{postId}")
    public Result<Void> deleteByAuthor(Authentication authentication, @PathVariable UUID postId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        postPublishingApplicationService.deleteByAuthor(userId, postId);
        return Result.ok();
    }

    @PutMapping("/{postId}/comments/{commentId}")
    public Result<Void> updateComment(
            Authentication authentication,
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        commentApplicationService.updateComment(userId, postId, commentId, request.getContent());
        return Result.ok();
    }

    @GetMapping("/{postId}/comments/{commentId}/replies")
    public Result<List<CommentResponse>> replies(
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(commentReadApplicationService.replyResponses(postId, commentId, page, size));
    }

    @PostMapping("/{postId}/top")
    public Result<Void> top(Authentication authentication, @PathVariable UUID postId) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        postModerationApplicationService.top(actorUserId, postId);
        return Result.ok();
    }

    @PostMapping("/{postId}/wonderful")
    public Result<Void> wonderful(Authentication authentication, @PathVariable UUID postId) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        postModerationApplicationService.wonderful(actorUserId, postId);
        return Result.ok();
    }

    @PostMapping("/{postId}/delete")
    public Result<Void> delete(Authentication authentication, @PathVariable UUID postId) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        postModerationApplicationService.delete(actorUserId, postId);
        return Result.ok();
    }

    private CreatePostResponse toCreatePostResponse(UUID postId) {
        CreatePostResponse response = new CreatePostResponse();
        response.setPostId(postId);
        return response;
    }
}
