// social-service 内部只读接口：供其它服务聚合展示使用，避免跨服务透传 Authorization 造成耦合。
package com.nowcoder.community.social.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.like.LikeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/social/read")
public class InternalSocialReadController {

    private final LikeService likeService;
    private final FollowService followService;

    public InternalSocialReadController(LikeService likeService, FollowService followService) {
        this.likeService = likeService;
        this.followService = followService;
    }

    @GetMapping("/likes/users/{userId}/count")
    public Result<Long> userLikeCount(@PathVariable int userId) {
        return Result.ok(likeService.userLikeCount(userId));
    }

    @GetMapping("/follows/{userId}/followees/count")
    public Result<Long> followeeCount(@PathVariable int userId, @RequestParam int entityType) {
        return Result.ok(followService.followeeCount(userId, entityType));
    }

    @GetMapping("/follows/{userId}/followers/count")
    public Result<Long> followerCount(@PathVariable int userId, @RequestParam int entityType) {
        return Result.ok(followService.followerCount(entityType, userId));
    }

    @GetMapping("/follows/status")
    public Result<Boolean> hasFollowed(
            @RequestParam int userId,
            @RequestParam int entityType,
            @RequestParam int entityId
    ) {
        return Result.ok(followService.hasFollowed(userId, entityType, entityId));
    }
}

