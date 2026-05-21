package com.nowcoder.community.social.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.social.application.FollowApplicationService;
import com.nowcoder.community.social.application.command.FollowCommand;
import com.nowcoder.community.social.application.command.UnfollowCommand;
import com.nowcoder.community.social.application.result.FollowRelationResult;
import com.nowcoder.community.social.controller.dto.FollowItem;
import com.nowcoder.community.social.controller.dto.FollowRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private static final int ENTITY_TYPE_USER = EntityTypes.USER;

    private final FollowApplicationService followApplicationService;

    public FollowController(FollowApplicationService followApplicationService) {
        this.followApplicationService = followApplicationService;
    }

    @PostMapping
    public Result<Void> follow(Authentication authentication, @Valid @RequestBody FollowRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        followApplicationService.follow(new FollowCommand(userId, request.getEntityType(), request.getEntityId()));
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> unfollow(Authentication authentication, @RequestParam int entityType, @RequestParam UUID entityId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        followApplicationService.unfollow(new UnfollowCommand(userId, entityType, entityId));
        return Result.ok();
    }

    @GetMapping("/status")
    public Result<Boolean> status(Authentication authentication, @RequestParam int entityType, @RequestParam UUID entityId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(followApplicationService.hasFollowed(userId, entityType, entityId));
    }

    @GetMapping("/{userId}/followees")
    public Result<List<FollowItem>> followees(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer entityType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int t = entityType == null ? ENTITY_TYPE_USER : entityType;
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        return Result.ok(followApplicationService.listFollowees(userId, t, p, s)
                .stream()
                .map(this::toItem)
                .toList());
    }

    @GetMapping("/{userId}/followers")
    public Result<List<FollowItem>> followers(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer entityType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int t = entityType == null ? ENTITY_TYPE_USER : entityType;
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        return Result.ok(followApplicationService.listFollowers(t, userId, p, s)
                .stream()
                .map(this::toItem)
                .toList());
    }

    @GetMapping("/{userId}/followees/count")
    public Result<Long> followeeCount(@PathVariable UUID userId, @RequestParam(required = false) Integer entityType) {
        int t = entityType == null ? ENTITY_TYPE_USER : entityType;
        return Result.ok(followApplicationService.followeeCount(userId, t));
    }

    @GetMapping("/{userId}/followers/count")
    public Result<Long> followerCount(@PathVariable UUID userId, @RequestParam(required = false) Integer entityType) {
        int t = entityType == null ? ENTITY_TYPE_USER : entityType;
        return Result.ok(followApplicationService.followerCount(t, userId));
    }

    private FollowItem toItem(FollowRelationResult result) {
        FollowItem item = new FollowItem();
        item.setTargetId(result.targetId());
        item.setFollowTime(result.followTime());
        return item;
    }
}
