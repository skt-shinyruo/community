package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.logging.SecurityEventLogger;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.user.application.UserAvatarApplicationService;
import com.nowcoder.community.user.application.UserProfileApplicationService;
import com.nowcoder.community.user.application.UserReadApplicationService;
import com.nowcoder.community.user.application.command.CreateAvatarUploadSessionCommand;
import com.nowcoder.community.user.application.result.AvatarUploadSessionResult;
import com.nowcoder.community.user.application.result.UserProfilePageResult;
import com.nowcoder.community.user.application.result.UserSummaryResult;
import com.nowcoder.community.user.controller.dto.AvatarUploadSessionRequest;
import com.nowcoder.community.user.controller.dto.AvatarUploadSessionResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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
        resp.setUserLevelEnabled(user.userLevelEnabled());
        resp.setUserLevel(user.userLevel());
        resp.setSignInDaysInWindow(user.signInDaysInWindow());
        resp.setLikeCount(user.likeCount());
        resp.setFolloweeCount(user.followeeCount());
        resp.setFollowerCount(user.followerCount());
        resp.setHasFollowed(user.hasFollowed());
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

    @PostMapping("/{userId}/avatar/upload-sessions")
    public Result<AvatarUploadSessionResponse> createAvatarUploadSession(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody AvatarUploadSessionRequest request
    ) {
        UUID currentUserId = CurrentUser.requireUserUuid(authentication);
        AvatarUploadSessionResponse response = toAvatarUploadSessionResponse(userAvatarApplicationService.createUploadSession(
                currentUserId,
                userId,
                toCreateAvatarUploadSessionCommand(request)
        ));
        SecurityEventLogger.info(
                log,
                "avatar_upload_session",
                "success",
                "user.id", userId,
                "community.target_type", "user",
                "community.target_id", userId,
                "community.avatar_object_id", response == null ? null : response.getObjectId()
        );
        return Result.ok(response);
    }

    @PutMapping("/{userId}/avatar")
    public Result<Void> updateAvatar(Authentication authentication, @PathVariable UUID userId, @Valid @RequestBody UpdateAvatarRequest request) {
        UUID currentUserId = CurrentUser.requireUserUuid(authentication);
        userAvatarApplicationService.updateAvatar(currentUserId, userId, request.getObjectId());
        SecurityEventLogger.info(
                log,
                "avatar_update",
                "success",
                "user.id", userId,
                "community.target_type", "user",
                "community.target_id", userId,
                "community.avatar_object_id", request.getObjectId()
        );
        return Result.ok();
    }

    private static CreateAvatarUploadSessionCommand toCreateAvatarUploadSessionCommand(AvatarUploadSessionRequest request) {
        return new CreateAvatarUploadSessionCommand(
                request == null ? "" : request.getFileName(),
                request == null ? "" : request.getContentType(),
                request == null ? 0 : request.getContentLength(),
                request == null ? "" : request.getChecksumSha256()
        );
    }

    private static AvatarUploadSessionResponse toAvatarUploadSessionResponse(AvatarUploadSessionResult session) {
        if (session == null) {
            return null;
        }
        AvatarUploadSessionResponse response = new AvatarUploadSessionResponse();
        response.setUploadId(session.uploadId());
        response.setObjectId(session.objectId() == null ? "" : session.objectId().toString());
        response.setVersionId(session.versionId() == null ? "" : session.versionId().toString());

        AvatarUploadSessionResponse.UploadInstruction upload = new AvatarUploadSessionResponse.UploadInstruction();
        upload.setUrl(session.uploadUrl());
        upload.setMethod(session.uploadMethod());
        upload.setFileField(session.fileField());
        upload.setFields(session.fields());
        upload.setHeaders(session.headers());
        response.setUpload(upload);

        AvatarUploadSessionResponse.Constraints constraints = new AvatarUploadSessionResponse.Constraints();
        constraints.setMaxBytes(session.maxBytes());
        constraints.setMimeTypes(session.mimeTypes());
        response.setConstraints(constraints);
        response.setExpiresAt(session.expiresAt() == null ? "" : session.expiresAt().toString());
        return response;
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
