package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.FeedReadApplicationService;
import com.nowcoder.community.content.application.result.FeedPageResult;
import com.nowcoder.community.content.controller.dto.FeedPageResponse;
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

    public FeedController(FeedReadApplicationService feedReadApplicationService) {
        this.feedReadApplicationService = feedReadApplicationService;
    }

    @GetMapping("/feed/global")
    public Result<FeedPageResponse> global(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        FeedPageResult page = feedReadApplicationService.listGlobalHotFeed(currentUserId, cursor, size == null ? 20 : size);
        return Result.ok(FeedPageResponse.from(page));
    }

    @GetMapping("/boards/{boardId}/feed")
    public Result<FeedPageResponse> board(
            Authentication authentication,
            @PathVariable UUID boardId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        FeedPageResult page = feedReadApplicationService.listBoardHotFeed(currentUserId, boardId, cursor, size == null ? 20 : size);
        return Result.ok(FeedPageResponse.from(page));
    }
}
