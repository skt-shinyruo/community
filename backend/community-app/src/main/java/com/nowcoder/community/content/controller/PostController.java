package com.nowcoder.community.content.controller;

import com.nowcoder.community.content.application.CommentApplicationService.CreateCommentCommand;
import com.nowcoder.community.content.application.PostCounterApplicationService;
import com.nowcoder.community.content.application.PostCounterApplicationService.RecordPostViewCommand;
import com.nowcoder.community.content.application.PostPublishingApplicationService;
import com.nowcoder.community.content.application.PostPublishingApplicationService.CreatePostCommand;
import com.nowcoder.community.content.application.PostPublishingApplicationService.PostContentBlockCommand;
import com.nowcoder.community.content.application.PostPublishingApplicationService.PostCreateResult;
import com.nowcoder.community.content.application.result.CommentPageResult;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.controller.dto.BatchPostSummaryRequest;
import com.nowcoder.community.content.controller.dto.CreateCommentRequest;
import com.nowcoder.community.content.controller.dto.CreatePostRequest;
import com.nowcoder.community.content.controller.dto.PostContentBlockRequest;
import com.nowcoder.community.content.controller.dto.UpdateCommentRequest;
import com.nowcoder.community.content.controller.dto.UpdatePostRequest;
import com.nowcoder.community.content.application.CommentApplicationService;
import com.nowcoder.community.content.application.CommentReadApplicationService;
import com.nowcoder.community.content.application.PostModerationApplicationService;
import com.nowcoder.community.content.application.PostReadApplicationService;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
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

import java.time.Clock;
import java.util.List;
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
    private final ClientIpResolver clientIpResolver;
    private final Clock clock;

    public PostController(
            PostReadApplicationService postReadApplicationService,
            CommentReadApplicationService commentReadApplicationService,
            PostPublishingApplicationService postPublishingApplicationService,
            PostModerationApplicationService postModerationApplicationService,
            CommentApplicationService commentApplicationService,
            PostCounterApplicationService postCounterApplicationService,
            ClientIpResolver clientIpResolver,
            Clock clock
    ) {
        this.postReadApplicationService = postReadApplicationService;
        this.commentReadApplicationService = commentReadApplicationService;
        this.postPublishingApplicationService = postPublishingApplicationService;
        this.postModerationApplicationService = postModerationApplicationService;
        this.commentApplicationService = commentApplicationService;
        this.postCounterApplicationService = postCounterApplicationService;
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @PostMapping
    public Result<PostCreateResult> create(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePostRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        PostCreateResult createResult = postPublishingApplicationService.create(
                idempotencyKey,
                new CreatePostCommand(
                        userId,
                        request.title(),
                        request.categoryId(),
                        request.tags(),
                        toBlockCommands(request.blocks())
                )
        );
        return Result.ok(createResult);
    }

    @PostMapping("/batch-summary")
    public Result<List<PostSummaryResult>> batchSummary(@Valid @RequestBody BatchPostSummaryRequest request) {
        List<UUID> postIds = request == null ? List.of() : request.postIds();
        List<PostSummaryResult> posts = postReadApplicationService.listPostsByIds(postIds);
        return Result.ok(posts == null ? List.of() : posts);
    }

    @GetMapping("/{postId}")
    public Result<PostDetailResult> detail(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable UUID postId
    ) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        PostDetailResult detail = postReadApplicationService.getPostDetail(currentUserId, postId);
        postCounterApplicationService.recordView(new RecordPostViewCommand(
                postId,
                viewerFingerprint(authentication, request),
                clock.instant()
        ));
        return Result.ok(detail);
    }

    @GetMapping("/{postId}/comments")
    public Result<CommentPageResult> comments(
            @PathVariable UUID postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(normalizeCommentPage(
                commentReadApplicationService.listRootComments(postId, cursor, size)));
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
                        request.parentCommentId(),
                        request.content()
                )
        ).commentId());
    }

    @PutMapping("/{postId}")
    public Result<Void> updatePost(Authentication authentication, @PathVariable UUID postId, @Valid @RequestBody UpdatePostRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        postPublishingApplicationService.updatePost(
                userId,
                postId,
                request.title(),
                request.categoryId(),
                request.tags(),
                toBlockCommands(request.blocks())
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
        commentApplicationService.updateComment(userId, postId, commentId, request.content());
        return Result.ok();
    }

    @GetMapping("/{postId}/comments/{commentId}/replies")
    public Result<CommentPageResult> replies(
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(normalizeCommentPage(
                commentReadApplicationService.listReplies(postId, commentId, cursor, size)));
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

    private static List<PostContentBlockCommand> toBlockCommands(List<PostContentBlockRequest> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream()
                .map(block -> new PostContentBlockCommand(
                        block.type(),
                        block.text(),
                        block.assetId(),
                        block.language(),
                        block.caption(),
                        block.displayName(),
                        block.metadata()
                ))
                .toList();
    }

    private static CommentPageResult normalizeCommentPage(CommentPageResult page) {
        return page == null ? new CommentPageResult(List.of(), "") : page;
    }

    private String viewerFingerprint(Authentication authentication, HttpServletRequest request) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        if (currentUserId != null) {
            return "auth:" + currentUserId;
        }
        ClientIpResolver.ResolvedClientIp resolvedClientIp = clientIpResolver.resolve(request);
        String clientIp = resolvedClientIp == null ? null : resolvedClientIp.ip();
        return "anon:" + Objects.toString(clientIp, "unknown") + "|" + userAgent(request);
    }

    private static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        return Objects.toString(request.getHeader("User-Agent"), "");
    }
}
