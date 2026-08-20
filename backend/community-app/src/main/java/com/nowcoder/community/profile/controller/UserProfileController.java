package com.nowcoder.community.profile.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.api.query.PostReadQueryApi;
import com.nowcoder.community.profile.application.UserProfileQueryApplicationService;
import com.nowcoder.community.profile.application.result.UserProfilePageResult;
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
    public Result<UserProfilePageResult> getUser(@PathVariable UUID userId) {
        return Result.ok(applicationService.get(userId));
    }

    @GetMapping("/{userId}/recent-posts")
    public Result<List<PostReadQueryApi.PostSummaryView>> recentPosts(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(applicationService.listRecentPosts(userId, page, size));
    }

    @GetMapping("/{userId}/recent-comments")
    public Result<List<PostReadQueryApi.RecentUserCommentView>> recentComments(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(applicationService.listRecentComments(userId, page, size));
    }
}
