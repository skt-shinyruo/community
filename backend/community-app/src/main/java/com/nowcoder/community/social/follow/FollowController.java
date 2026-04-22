package com.nowcoder.community.social.follow;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.social.follow.dto.FollowItem;
import com.nowcoder.community.social.follow.dto.FollowRequest;
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

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private static final int ENTITY_TYPE_USER = EntityTypes.USER;

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping
    public Result<Void> follow(Authentication authentication, @Valid @RequestBody FollowRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        if (request.getEntityType() != ENTITY_TYPE_USER) {
            throw new BusinessException(INVALID_ARGUMENT, "follow 仅支持 USER");
        }
        followService.follow(userId, request);
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> unfollow(Authentication authentication, @RequestParam int entityType, @RequestParam UUID entityId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        if (entityType != ENTITY_TYPE_USER) {
            throw new BusinessException(INVALID_ARGUMENT, "unfollow 仅支持 USER");
        }
        followService.unfollow(userId, entityType, entityId);
        return Result.ok();
    }

    @GetMapping("/status")
    public Result<Boolean> status(Authentication authentication, @RequestParam int entityType, @RequestParam UUID entityId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        if (entityType != ENTITY_TYPE_USER) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return Result.ok(followService.hasFollowed(userId, entityType, entityId));
    }

    @GetMapping("/{userId}/followees")
    public Result<List<FollowItem>> followees(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer entityType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int t = entityType == null ? ENTITY_TYPE_USER : entityType;
        if (!EntityTypes.isValid(t)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        return Result.ok(followService.listFollowees(userId, t, p, s));
    }

    @GetMapping("/{userId}/followers")
    public Result<List<FollowItem>> followers(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer entityType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int t = entityType == null ? ENTITY_TYPE_USER : entityType;
        if (!EntityTypes.isValid(t)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        return Result.ok(followService.listFollowers(t, userId, p, s));
    }

    @GetMapping("/{userId}/followees/count")
    public Result<Long> followeeCount(@PathVariable UUID userId, @RequestParam(required = false) Integer entityType) {
        int t = entityType == null ? ENTITY_TYPE_USER : entityType;
        if (!EntityTypes.isValid(t)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return Result.ok(followService.followeeCount(userId, t));
    }

    @GetMapping("/{userId}/followers/count")
    public Result<Long> followerCount(@PathVariable UUID userId, @RequestParam(required = false) Integer entityType) {
        int t = entityType == null ? ENTITY_TYPE_USER : entityType;
        if (!EntityTypes.isValid(t)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return Result.ok(followService.followerCount(t, userId));
    }
}
