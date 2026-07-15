package com.nowcoder.community.social.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.social.application.LikeApplicationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/likes")
public class LikeController {

    private final LikeApplicationService likeApplicationService;

    public LikeController(LikeApplicationService likeApplicationService) {
        this.likeApplicationService = likeApplicationService;
    }

    @GetMapping("/status")
    public Result<Boolean> status(
            Authentication authentication,
            @RequestParam int entityType,
            @RequestParam UUID entityId
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(likeApplicationService.isLiked(userId, entityType, entityId));
    }

    @GetMapping("/count")
    public Result<Long> count(@RequestParam int entityType, @RequestParam UUID entityId) {
        return Result.ok(likeApplicationService.count(entityType, entityId));
    }

    @GetMapping("/counts")
    public Result<Map<UUID, Long>> counts(
            @RequestParam int entityType,
            @RequestParam(required = false) List<UUID> entityIds
    ) {
        return Result.ok(likeApplicationService.counts(entityType, entityIds));
    }

    @GetMapping("/statuses")
    public Result<Map<UUID, Boolean>> statuses(
            Authentication authentication,
            @RequestParam int entityType,
            @RequestParam(required = false) List<UUID> entityIds
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(likeApplicationService.statuses(userId, entityType, entityIds));
    }

}
