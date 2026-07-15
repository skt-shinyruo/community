package com.nowcoder.community.profile.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.profile.application.UserProfileQueryApplicationService;
import com.nowcoder.community.profile.application.result.UserProfilePageResult;
import com.nowcoder.community.profile.controller.dto.UserProfilePostSummaryResponse;
import com.nowcoder.community.profile.controller.dto.UserProfileResponse;
import com.nowcoder.community.profile.controller.dto.UserRecentCommentItemResponse;
import org.springframework.security.core.Authentication;
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
    public Result<UserProfileResponse> getUser(Authentication authentication, @PathVariable UUID userId) {
        UUID viewerId = CurrentUser.tryUserUuid(authentication);
        UserProfilePageResult user = applicationService.get(viewerId, userId);
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
        response.setHasFollowed(user.hasFollowed());
        return Result.ok(response);
    }

    @GetMapping("/{userId}/recent-posts")
    public Result<List<UserProfilePostSummaryResponse>> recentPosts(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(applicationService.listRecentPosts(userId, page, size).stream()
                .map(UserProfileController::toPostResponse)
                .toList());
    }

    @GetMapping("/{userId}/recent-comments")
    public Result<List<UserRecentCommentItemResponse>> recentComments(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return Result.ok(applicationService.listRecentComments(userId, page, size).stream()
                .map(UserProfileController::toCommentResponse)
                .toList());
    }

    private static UserProfilePostSummaryResponse toPostResponse(UserProfilePageResult.RecentPostSummaryResult view) {
        UserProfilePostSummaryResponse response = new UserProfilePostSummaryResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setTitle(view.title());
        response.setType(view.type());
        response.setStatus(view.status());
        response.setCreateTime(view.createTime());
        response.setCommentCount(view.commentCount());
        response.setScore(view.score());
        response.setCategoryId(view.categoryId());
        response.setTags(view.tags());
        response.setLastReplyUserId(view.lastReplyUserId());
        response.setLastReplyTime(view.lastReplyTime());
        response.setLastActivityTime(view.lastActivityTime());
        response.setLastReplyPreview(view.lastReplyPreview());
        return response;
    }

    private static UserRecentCommentItemResponse toCommentResponse(UserProfilePageResult.RecentCommentItemResult view) {
        UserRecentCommentItemResponse response = new UserRecentCommentItemResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setEntityType(view.entityType());
        response.setEntityId(view.entityId());
        response.setTargetId(view.targetId());
        response.setPostId(view.postId());
        response.setPostTitle(view.postTitle());
        response.setContent(view.content());
        response.setCreateTime(view.createTime());
        return response;
    }
}
