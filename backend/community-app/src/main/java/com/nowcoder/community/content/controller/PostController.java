package com.nowcoder.community.content.controller;

import com.nowcoder.community.content.application.command.CreateCommentCommand;
import com.nowcoder.community.content.application.command.CreatePostCommand;
import com.nowcoder.community.content.application.command.PostContentBlockCommand;
import com.nowcoder.community.content.application.command.RecordPostViewCommand;
import com.nowcoder.community.content.application.PostCounterApplicationService;
import com.nowcoder.community.content.application.PostPublishingApplicationService;
import com.nowcoder.community.content.application.result.CommentPageResult;
import com.nowcoder.community.content.application.result.CommentResult;
import com.nowcoder.community.content.application.result.PostCreateResult;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.controller.dto.CommentPageResponse;
import com.nowcoder.community.content.controller.dto.CommentResponse;
import com.nowcoder.community.content.controller.dto.BatchPostSummaryRequest;
import com.nowcoder.community.content.controller.dto.CreateCommentRequest;
import com.nowcoder.community.content.controller.dto.CreatePostRequest;
import com.nowcoder.community.content.controller.dto.CreatePostResponse;
import com.nowcoder.community.content.controller.dto.PostDetailResponse;
import com.nowcoder.community.content.controller.dto.PostContentBlockRequest;
import com.nowcoder.community.content.controller.dto.PostSummaryResponse;
import com.nowcoder.community.content.controller.dto.UpdateCommentRequest;
import com.nowcoder.community.content.controller.dto.UpdatePostRequest;
import com.nowcoder.community.content.application.CommentApplicationService;
import com.nowcoder.community.content.application.CommentReadApplicationService;
import com.nowcoder.community.content.application.PostModerationApplicationService;
import com.nowcoder.community.content.application.PostReadApplicationService;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostReadApplicationService postReadApplicationService;
    private final CommentReadApplicationService commentReadApplicationService;
    private final PostPublishingApplicationService postPublishingApplicationService;
    private final PostModerationApplicationService postModerationApplicationService;
    private final CommentApplicationService commentApplicationService;
    private final PostCounterApplicationService postCounterApplicationService;

    public PostController(
            PostReadApplicationService postReadApplicationService,
            CommentReadApplicationService commentReadApplicationService,
            PostPublishingApplicationService postPublishingApplicationService,
            PostModerationApplicationService postModerationApplicationService,
            CommentApplicationService commentApplicationService,
            PostCounterApplicationService postCounterApplicationService
    ) {
        this.postReadApplicationService = postReadApplicationService;
        this.commentReadApplicationService = commentReadApplicationService;
        this.postPublishingApplicationService = postPublishingApplicationService;
        this.postModerationApplicationService = postModerationApplicationService;
        this.commentApplicationService = commentApplicationService;
        this.postCounterApplicationService = postCounterApplicationService;
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
        List<PostSummaryResult> posts = postReadApplicationService.listPosts(currentUserId, order, categoryId, tag, subscribed, page, size);
        return Result.ok(toPostSummaryResponses(posts));
    }

    @PostMapping
    public Result<CreatePostResponse> create(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePostRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        PostCreateResult createResult = postPublishingApplicationService.create(
                idempotencyKey,
                new CreatePostCommand(
                        userId,
                        request.getTitle(),
                        request.getCategoryId(),
                        request.getTags(),
                        toBlockCommands(request.getBlocks())
                )
        );
        return Result.ok(CreatePostResponse.from(createResult));
    }

    @PostMapping("/batch-summary")
    public Result<List<PostSummaryResponse>> batchSummary(@Valid @RequestBody BatchPostSummaryRequest request) {
        List<UUID> postIds = request == null ? List.of() : request.getPostIds();
        List<PostSummaryResult> posts = postReadApplicationService.listPostsByIds(postIds);
        return Result.ok(toPostSummaryResponses(posts));
    }

    @GetMapping("/{postId}")
    public Result<PostDetailResponse> detail(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable UUID postId
    ) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        PostDetailResult detail = postReadApplicationService.getPostDetail(currentUserId, postId);
        postCounterApplicationService.recordView(new RecordPostViewCommand(
                postId,
                viewerFingerprint(authentication, request),
                Instant.now()
        ));
        return Result.ok(PostDetailResponse.from(detail));
    }

    @GetMapping("/{postId}/comments")
    public Result<CommentPageResponse> comments(
            @PathVariable UUID postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        CommentPageResult comments = commentReadApplicationService.listRootComments(postId, cursor, size);
        return Result.ok(CommentPageResponse.from(comments));
    }

    @PostMapping("/{postId}/comments")
    public Result<UUID> addComment(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(commentApplicationService.create(
                idempotencyKey,
                new CreateCommentCommand(
                        userId,
                        postId,
                        request.getParentCommentId(),
                        request.getReplyToUserId(),
                        request.getContent()
                )
        ).commentId());
    }

    @PutMapping("/{postId}")
    public Result<Void> updatePost(Authentication authentication, @PathVariable UUID postId, @Valid @RequestBody UpdatePostRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        postPublishingApplicationService.updatePost(
                userId,
                postId,
                request.getTitle(),
                request.getCategoryId(),
                request.getTags(),
                toBlockCommands(request.getBlocks())
        );
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
    public Result<CommentPageResponse> replies(
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        CommentPageResult replies = commentReadApplicationService.listReplies(postId, commentId, cursor, size);
        return Result.ok(CommentPageResponse.from(replies));
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

    private static List<PostSummaryResponse> toPostSummaryResponses(List<PostSummaryResult> views) {
        if (views == null || views.isEmpty()) {
            return List.of();
        }
        return views.stream().map(PostSummaryResponse::from).toList();
    }

    private static List<PostContentBlockCommand> toBlockCommands(List<PostContentBlockRequest> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream()
                .map(block -> new PostContentBlockCommand(
                        block.getType(),
                        block.getText(),
                        block.getAssetId(),
                        block.getLanguage(),
                        block.getCaption(),
                        block.getDisplayName(),
                        block.getMetadata()
                ))
                .toList();
    }

    private static String viewerFingerprint(Authentication authentication, HttpServletRequest request) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        if (currentUserId != null) {
            return "auth:" + currentUserId;
        }
        return "anon:" + remoteAddress(request) + "|" + userAgent(request);
    }

    private static String remoteAddress(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return Objects.toString(request.getRemoteAddr(), "");
    }

    private static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        return Objects.toString(request.getHeader("User-Agent"), "");
    }
}
