package com.nowcoder.community.profile.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.profile.application.UserProfileQueryApplicationService;
import com.nowcoder.community.profile.application.result.UserProfilePageResult;
import com.nowcoder.community.profile.controller.dto.UserProfileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserProfileQueryApplicationService applicationService;

    public UserProfileController(UserProfileQueryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/{userId}")
    public Result<UserProfileResponse> getUser(@PathVariable UUID userId) {
        UserProfilePageResult user = applicationService.get(userId);
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.userId());
        response.setUsername(user.username());
        response.setHeaderUrl(user.headerUrl());
        response.setType(user.type());
        response.setStatus(user.status());
        response.setCreateTime(user.createTime());
        response.setUserLevelEnabled(user.userLevelEnabled());
        response.setUserLevel(user.userLevel());
        response.setSignInDaysInWindow(user.signInDaysInWindow());
        response.setLikeCount(user.likeCount());
        response.setFolloweeCount(user.followeeCount());
        response.setFollowerCount(user.followerCount());
        return Result.ok(response);
    }

    @GetMapping("/{userId}/recent-posts")
    public Result<List<UserProfilePageResult.RecentPostSummaryResult>> recentPosts(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(applicationService.listRecentPosts(userId, page, size));
    }

    @GetMapping("/{userId}/recent-comments")
    public Result<List<UserProfilePageResult.RecentCommentItemResult>> recentComments(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(applicationService.listRecentComments(userId, page, size));
    }
}
