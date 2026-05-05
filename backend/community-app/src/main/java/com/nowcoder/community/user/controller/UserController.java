package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.logging.SecurityEventLogger;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.user.application.AvatarUploadContent;
import com.nowcoder.community.user.application.UserAvatarApplicationService;
import com.nowcoder.community.user.application.UserProfileApplicationService;
import com.nowcoder.community.user.application.UserReadApplicationService;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;
import com.nowcoder.community.user.application.result.UserProfilePageResult;
import com.nowcoder.community.user.application.result.UserSummaryResult;
import com.nowcoder.community.user.controller.dto.AvatarUploadTokenResponse;
import com.nowcoder.community.user.controller.dto.BatchUserSummaryRequest;
import com.nowcoder.community.user.controller.dto.UpdateAvatarRequest;
import com.nowcoder.community.user.controller.dto.UserProfilePostSummaryResponse;
import com.nowcoder.community.user.controller.dto.UserProfileResponse;
import com.nowcoder.community.user.controller.dto.UserRecentCommentItemResponse;
import com.nowcoder.community.user.controller.dto.UserSummaryResponse;
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

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserReadApplicationService userReadApplicationService;
    private final UserProfileApplicationService userProfileApplicationService;
    private final UserAvatarApplicationService userAvatarApplicationService;

    public UserController(UserReadApplicationService userReadApplicationService,
                          UserProfileApplicationService userProfileApplicationService,
                          UserAvatarApplicationService userAvatarApplicationService) {
        this.userReadApplicationService = userReadApplicationService;
        this.userProfileApplicationService = userProfileApplicationService;
        this.userAvatarApplicationService = userAvatarApplicationService;
    }

    @GetMapping("/{userId}")
    public Result<UserProfileResponse> getUser(Authentication authentication, @PathVariable UUID userId) {
        UUID viewerId = CurrentUser.tryUserUuid(authentication);
        UserProfilePageResult user = userProfileApplicationService.get(viewerId, userId);
        UserProfileResponse resp = new UserProfileResponse();
        resp.setId(user.userId());
        resp.setUsername(user.username());
        resp.setHeaderUrl(user.headerUrl());
        resp.setType(user.type());
        resp.setStatus(user.status());
        resp.setCreateTime(user.createTime());
        resp.setScore(user.score());
        resp.setLevel(user.level());
        resp.setUserLevelEnabled(user.userLevelEnabled());
        resp.setUserLevel(user.userLevel());
        resp.setSignInDaysInWindow(user.signInDaysInWindow());
        resp.setLikeCount(user.likeCount());
        resp.setFolloweeCount(user.followeeCount());
        resp.setFollowerCount(user.followerCount());
        resp.setHasFollowed(user.hasFollowed());
        resp.setSocialDegraded(user.socialDegraded());
        return Result.ok(resp);
    }

    @GetMapping("/{userId}/recent-posts")
    public Result<List<UserProfilePostSummaryResponse>> recentPosts(@PathVariable UUID userId,
                                                                    @RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer size) {
        return Result.ok(userProfileApplicationService.listRecentPosts(userId, page, size).stream()
                .map(UserController::toUserProfilePostSummaryResponse)
                .toList());
    }

    @GetMapping("/{userId}/recent-comments")
    public Result<List<UserRecentCommentItemResponse>> recentComments(@PathVariable UUID userId,
                                                                      @RequestParam(required = false) Integer page,
                                                                      @RequestParam(required = false) Integer size) {
        return Result.ok(userProfileApplicationService.listRecentComments(userId, page, size).stream()
                .map(UserController::toUserRecentCommentItemResponse)
                .toList());
    }

    @PostMapping("/batch-summary")
    public Result<List<UserSummaryResponse>> batchSummary(@Valid @RequestBody BatchUserSummaryRequest request) {
        List<UUID> raw = request == null ? null : request.getUserIds();
        return Result.ok(userReadApplicationService.listSummaryResultsByIds(raw).stream()
                .map(UserController::toUserSummaryResponse)
                .toList());
    }

    @GetMapping("/{userId}/avatar/upload-token")
    public Result<AvatarUploadTokenResponse> uploadToken(Authentication authentication, @PathVariable UUID userId) {
        UUID currentUserId = CurrentUser.requireUserUuid(authentication);
        AvatarUploadTokenResponse response = toAvatarUploadTokenResponse(userAvatarApplicationService.createUploadToken(currentUserId, userId));
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
    public Result<Void> uploadAvatar(Authentication authentication, @PathVariable UUID userId, @RequestParam("file") MultipartFile file, @RequestParam("fileName") String fileName) {
        UUID currentUserId = CurrentUser.requireUserUuid(authentication);
        userAvatarApplicationService.upload(currentUserId, userId, fileName, toAvatarUploadContent(file));
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
    public Result<Void> updateAvatar(Authentication authentication, @PathVariable UUID userId, @Valid @RequestBody UpdateAvatarRequest request) {
        UUID currentUserId = CurrentUser.requireUserUuid(authentication);
        userAvatarApplicationService.updateAvatar(currentUserId, userId, request.getFileName());
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

    private static AvatarUploadTokenResponse toAvatarUploadTokenResponse(AvatarUploadTokenResult token) {
        if (token == null) {
            return null;
        }
        AvatarUploadTokenResponse response = new AvatarUploadTokenResponse();
        response.setProvider(token.provider());
        response.setUploadToken(token.uploadToken());
        response.setFileName(token.fileName());
        response.setBucketUrl(token.bucketUrl());
        response.setUploadUrl(token.uploadUrl());
        response.setUploadMethod(token.uploadMethod());
        response.setMaxBytes(token.maxBytes());
        response.setMimeLimit(token.mimeLimit());
        return response;
    }

    private static AvatarUploadContent toAvatarUploadContent(MultipartFile file) {
        return new AvatarUploadContent(
                () -> {
                    if (file == null) {
                        return java.io.InputStream.nullInputStream();
                    }
                    try {
                        return file.getInputStream();
                    } catch (IOException e) {
                        throw new BusinessException(INTERNAL_ERROR, "读取头像失败", e);
                    }
                },
                file == null ? "" : file.getContentType(),
                file == null ? 0 : file.getSize(),
                file == null || file.isEmpty()
        );
    }

    private static UserSummaryResponse toUserSummaryResponse(UserSummaryResult user) {
        UserSummaryResponse response = new UserSummaryResponse();
        response.setId(user.id());
        response.setUsername(user.username());
        response.setHeaderUrl(user.headerUrl());
        response.setType(user.type());
        return response;
    }

    private static UserProfilePostSummaryResponse toUserProfilePostSummaryResponse(UserProfilePageResult.RecentPostSummaryResult view) {
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

    private static UserRecentCommentItemResponse toUserRecentCommentItemResponse(UserProfilePageResult.RecentCommentItemResult view) {
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
