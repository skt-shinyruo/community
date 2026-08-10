package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.FeedReadApplicationService;
import com.nowcoder.community.content.application.FollowFeedReadApplicationService;
import com.nowcoder.community.content.application.result.FeedPageResult;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FeedController {

    private final FeedReadApplicationService feedReadApplicationService;
    private final FollowFeedReadApplicationService followFeedReadApplicationService;

    public FeedController(
            FeedReadApplicationService feedReadApplicationService,
            FollowFeedReadApplicationService followFeedReadApplicationService
    ) {
        this.feedReadApplicationService = feedReadApplicationService;
        this.followFeedReadApplicationService = followFeedReadApplicationService;
    }

    @GetMapping("/feed/global")
    public Result<FeedPageResult> global(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        return Result.ok(normalizePage(feedReadApplicationService.listGlobalHotFeed(
                currentUserId, cursor, size == null ? 20 : size)));
    }

    @GetMapping("/boards/{boardId}/feed")
    public Result<FeedPageResult> board(
            Authentication authentication,
            @PathVariable UUID boardId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        return Result.ok(normalizePage(feedReadApplicationService.listBoardHotFeed(
                currentUserId, boardId, cursor, size == null ? 20 : size)));
    }

    @GetMapping("/feed/follow")
    public Result<FeedPageResult> follow(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        return Result.ok(normalizePage(followFeedReadApplicationService.listFollowFeed(
                currentUserId, cursor, size == null ? 20 : size)));
    }

    private static FeedPageResult normalizePage(FeedPageResult page) {
        return page == null ? new FeedPageResult(null, null, null) : page;
    }
}
