package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.logging.SecurityEventLogger;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.growth.service.UserLevelService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.user.app.query.GetUserProfilePageQuery;
import com.nowcoder.community.user.app.query.UserProfilePageView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.dto.AvatarUploadTokenResponse;
import com.nowcoder.community.user.dto.BatchUserSummaryRequest;
import com.nowcoder.community.user.dto.UpdateAvatarRequest;
import com.nowcoder.community.user.dto.UserProfilePostSummaryResponse;
import com.nowcoder.community.user.dto.UserProfileResponse;
import com.nowcoder.community.user.dto.UserRecentCommentItemResponse;
import com.nowcoder.community.user.dto.UserResolveResponse;
import com.nowcoder.community.user.dto.UserSummaryResponse;
import com.nowcoder.community.user.service.AvatarService;
import com.nowcoder.community.user.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserLookupQueryApi userLookupQueryApi;
    private final GetUserProfilePageQuery getUserProfilePageQuery;
    private final UserService userService;
    private final AvatarService avatarService;
    private final UserLevelService userLevelService;

    public UserController(UserLookupQueryApi userLookupQueryApi,
                          GetUserProfilePageQuery getUserProfilePageQuery,
                          UserService userService,
                          AvatarService avatarService,
                          UserLevelService userLevelService) {
        this.userLookupQueryApi = userLookupQueryApi;
        this.getUserProfilePageQuery = getUserProfilePageQuery;
        this.userService = userService;
        this.avatarService = avatarService;
        this.userLevelService = userLevelService;
    }

    @GetMapping("/{userId}")
    public Result<UserProfileResponse> getUser(Authentication authentication, @PathVariable int userId) {
        UserProfilePageView user = getUserProfilePageQuery.get(authentication, userId);
        UserProfileResponse resp = new UserProfileResponse();
        resp.setId(user.userId());
        resp.setUsername(user.username());
        resp.setHeaderUrl(user.headerUrl());
        resp.setType(user.type());
        resp.setStatus(user.status());
        resp.setCreateTime(user.createTime());
        resp.setScore(user.score());
        resp.setLevel(user.level());
        UserLevelService.UserLevelSummary levelSummary = userLevelService.evaluateLevel(userId);
        resp.setUserLevelEnabled(levelSummary.enabled());
        if (levelSummary.enabled()) {
            resp.setUserLevel(levelSummary.userLevel());
            resp.setSignInDaysInWindow(levelSummary.signInDaysInWindow());
        } else {
            resp.setUserLevel(null);
            resp.setSignInDaysInWindow(null);
        }
        resp.setLikeCount(user.likeCount());
        resp.setFolloweeCount(user.followeeCount());
        resp.setFollowerCount(user.followerCount());
        resp.setHasFollowed(user.hasFollowed());
        resp.setSocialDegraded(user.socialDegraded());
        return Result.ok(resp);
    }

    @GetMapping("/{userId}/recent-posts")
    public Result<List<UserProfilePostSummaryResponse>> recentPosts(@PathVariable int userId,
                                                                    @RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer size) {
        return Result.ok(getUserProfilePageQuery.listRecentPosts(userId, page, size).stream()
                .map(UserController::toUserProfilePostSummaryResponse)
                .toList());
    }

    @GetMapping("/{userId}/recent-comments")
    public Result<List<UserRecentCommentItemResponse>> recentComments(@PathVariable int userId,
                                                                      @RequestParam(required = false) Integer page,
                                                                      @RequestParam(required = false) Integer size) {
        return Result.ok(getUserProfilePageQuery.listRecentComments(userId, page, size).stream()
                .map(UserController::toUserRecentCommentItemResponse)
                .toList());
    }

    @GetMapping("/resolve")
    public Result<UserResolveResponse> resolveByUsername(@RequestParam String username) {
        UserSummaryView user = userLookupQueryApi.getSummaryByUsername(username);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        UserResolveResponse resp = new UserResolveResponse();
        resp.setId(user.id());
        resp.setUsername(user.username());
        resp.setHeaderUrl(user.headerUrl());
        return Result.ok(resp);
    }

    @PostMapping("/batch-summary")
    public Result<List<UserSummaryResponse>> batchSummary(@Valid @RequestBody BatchUserSummaryRequest request) {
        List<Integer> raw = request == null ? List.of() : request.getUserIds();
        if (raw == null || raw.isEmpty()) {
            return Result.ok(List.of());
        }

        LinkedHashSet<Integer> dedup = new LinkedHashSet<>();
        for (Integer id : raw) {
            if (id == null || id <= 0) {
                continue;
            }
            dedup.add(id);
            if (dedup.size() >= 200) {
                break;
            }
        }
        if (dedup.isEmpty()) {
            return Result.ok(List.of());
        }

        List<Integer> ids = new ArrayList<>(dedup);
        List<UserSummaryView> users = userLookupQueryApi.listSummariesByIds(ids);
        Map<Integer, UserSummaryResponse> map = new HashMap<>();
        for (UserSummaryView u : users) {
            if (u == null || u.id() <= 0) {
                continue;
            }
            UserSummaryResponse s = new UserSummaryResponse();
            s.setId(u.id());
            s.setUsername(u.username());
            s.setHeaderUrl(u.headerUrl());
            s.setType(u.type());
            map.put(u.id(), s);
        }

        List<UserSummaryResponse> out = new ArrayList<>();
        for (Integer id : ids) {
            UserSummaryResponse s = map.get(id);
            if (s != null) {
                out.add(s);
            }
        }
        return Result.ok(out);
    }

    @GetMapping("/{userId}/avatar/upload-token")
    public Result<AvatarUploadTokenResponse> uploadToken(Authentication authentication, @PathVariable int userId) {
        int currentUserId = CurrentUser.requireUserId(authentication);
        if (currentUserId != userId) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
        AvatarUploadTokenResponse response = avatarService.createUploadToken(userId);
        SecurityEventLogger.info(
                log,
                "avatar_upload_token",
                "success",
                "user.id", userId,
                "community.target_type", "user",
                "community.target_id", userId,
                "community.avatar_provider", response == null ? null : response.getProvider(),
                "community.avatar_file_name", response == null ? null : response.getFileName()
        );
        return Result.ok(response);
    }

    @PostMapping(value = "/{userId}/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> uploadAvatar(Authentication authentication, @PathVariable int userId, @RequestParam("file") MultipartFile file, @RequestParam("fileName") String fileName) {
        int currentUserId = CurrentUser.requireUserId(authentication);
        if (currentUserId != userId) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
        avatarService.upload(userId, fileName, file);
        SecurityEventLogger.info(
                log,
                "avatar_upload",
                "success",
                "user.id", userId,
                "community.target_type", "user",
                "community.target_id", userId,
                "community.avatar_file_name", fileName,
                "community.file_content_type", file == null ? null : file.getContentType(),
                "community.file_size_bytes", file == null ? null : file.getSize()
        );
        return Result.ok();
    }

    @PutMapping("/{userId}/avatar")
    public Result<Void> updateAvatar(Authentication authentication, @PathVariable int userId, @Valid @RequestBody UpdateAvatarRequest request) {
        int currentUserId = CurrentUser.requireUserId(authentication);
        if (currentUserId != userId) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
        avatarService.assertAndConsumeUploadTicket(userId, request.getFileName());
        String url = avatarService.buildAvatarUrl(request.getFileName());
        userService.updateHeaderUrl(userId, url);
        SecurityEventLogger.info(
                log,
                "avatar_update",
                "success",
                "user.id", userId,
                "community.target_type", "user",
                "community.target_id", userId,
                "community.avatar_file_name", request.getFileName()
        );
        return Result.ok();
    }

    private static UserProfilePostSummaryResponse toUserProfilePostSummaryResponse(UserProfilePageView.RecentPostSummaryView view) {
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

    private static UserRecentCommentItemResponse toUserRecentCommentItemResponse(UserProfilePageView.RecentCommentItemView view) {
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
