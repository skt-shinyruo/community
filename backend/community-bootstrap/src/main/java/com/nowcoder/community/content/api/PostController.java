package com.nowcoder.community.content.api;

import com.nowcoder.community.content.api.dto.CommentResponse;
import com.nowcoder.community.content.api.dto.CreateCommentRequest;
import com.nowcoder.community.content.api.dto.CreatePostRequest;
import com.nowcoder.community.content.api.dto.CreatePostResponse;
import com.nowcoder.community.content.api.dto.PostDetailResponse;
import com.nowcoder.community.content.api.dto.PostSummaryResponse;
import com.nowcoder.community.content.api.dto.UpdateCommentRequest;
import com.nowcoder.community.content.api.dto.UpdatePostRequest;
import com.nowcoder.community.content.application.PostApplicationService;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
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

    private final PostApplicationService postApplicationService;

    public PostController(PostApplicationService postApplicationService) {
        this.postApplicationService = postApplicationService;
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
        Integer currentUserId = CurrentUser.tryUserId(authentication);
        return Result.ok(postApplicationService.list(currentUserId, order, categoryId, tag, subscribed, page, size));
    }

    @PostMapping
    public Result<CreatePostResponse> create(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePostRequest request
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(postApplicationService.create(userId, idempotencyKey, request));
    }

    @GetMapping("/{postId}")
    public Result<PostDetailResponse> detail(Authentication authentication, @PathVariable int postId) {
        Integer maybeCurrentUserId = CurrentUser.tryUserId(authentication);
        int currentUserId = maybeCurrentUserId == null ? 0 : maybeCurrentUserId;
        return Result.ok(postApplicationService.detail(currentUserId, postId));
    }

    @GetMapping("/{postId}/comments")
    public Result<List<CommentResponse>> comments(
            @PathVariable int postId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(postApplicationService.comments(postId, page, size));
    }

    @PostMapping("/{postId}/comments")
    public Result<Integer> addComment(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable int postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(postApplicationService.addComment(userId, idempotencyKey, postId, request));
    }

    @PutMapping("/{postId}")
    public Result<Void> updatePost(Authentication authentication, @PathVariable int postId, @Valid @RequestBody UpdatePostRequest request) {
        int userId = CurrentUser.requireUserId(authentication);
        postApplicationService.updatePost(userId, postId, request);
        return Result.ok();
    }

    @DeleteMapping("/{postId}")
    public Result<Void> deleteByAuthor(Authentication authentication, @PathVariable int postId) {
        int userId = CurrentUser.requireUserId(authentication);
        postApplicationService.deleteByAuthor(userId, postId);
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
        postApplicationService.updateComment(userId, postId, commentId, request);
        return Result.ok();
    }

    @GetMapping("/{postId}/comments/{commentId}/replies")
    public Result<List<CommentResponse>> replies(
            @PathVariable int postId,
            @PathVariable int commentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(postApplicationService.replies(postId, commentId, page, size));
    }

    @PostMapping("/{postId}/top")
    public Result<Void> top(Authentication authentication, @PathVariable int postId) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        postApplicationService.top(actorUserId, postId);
        return Result.ok();
    }

    @PostMapping("/{postId}/wonderful")
    public Result<Void> wonderful(Authentication authentication, @PathVariable int postId) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        postApplicationService.wonderful(actorUserId, postId);
        return Result.ok();
    }

    @PostMapping("/{postId}/delete")
    public Result<Void> delete(Authentication authentication, @PathVariable int postId) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        postApplicationService.delete(actorUserId, postId);
        return Result.ok();
    }
}
