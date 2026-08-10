package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.logging.SecurityEventLogger;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.user.application.UserAvatarApplicationService;
import com.nowcoder.community.user.application.UserReadApplicationService;
import com.nowcoder.community.user.application.command.CreateAvatarUploadSessionCommand;
import com.nowcoder.community.user.application.result.AvatarUploadSessionResult;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.controller.dto.AvatarUploadSessionRequest;
import com.nowcoder.community.user.controller.dto.AvatarUploadSessionResponse;
import com.nowcoder.community.user.controller.dto.BatchUserSummaryRequest;
import com.nowcoder.community.user.controller.dto.UpdateAvatarRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserReadApplicationService userReadApplicationService;
    private final UserAvatarApplicationService userAvatarApplicationService;

    public UserController(UserReadApplicationService userReadApplicationService,
                          UserAvatarApplicationService userAvatarApplicationService) {
        this.userReadApplicationService = Objects.requireNonNull(
                userReadApplicationService,
                "userReadApplicationService must not be null"
        );
        this.userAvatarApplicationService = Objects.requireNonNull(
                userAvatarApplicationService,
                "userAvatarApplicationService must not be null"
        );
    }

    @PostMapping("/batch-summary")
    public Result<List<UserSummaryView>> batchSummary(@Valid @RequestBody BatchUserSummaryRequest request) {
        List<UUID> raw = request == null ? null : request.userIds();
        return Result.ok(userReadApplicationService.listSummariesByIds(raw));
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
                "community.avatar_object_id", response == null ? null : response.objectId()
        );
        return Result.ok(response);
    }

    @PutMapping("/{userId}/avatar")
    public Result<Void> updateAvatar(Authentication authentication, @PathVariable UUID userId, @Valid @RequestBody UpdateAvatarRequest request) {
        UUID currentUserId = CurrentUser.requireUserUuid(authentication);
        userAvatarApplicationService.updateAvatar(currentUserId, userId, request.objectId());
        SecurityEventLogger.info(
                log,
                "avatar_update",
                "success",
                "user.id", userId,
                "community.target_type", "user",
                "community.target_id", userId,
                "community.avatar_object_id", request.objectId()
        );
        return Result.ok();
    }

    private static CreateAvatarUploadSessionCommand toCreateAvatarUploadSessionCommand(AvatarUploadSessionRequest request) {
        return new CreateAvatarUploadSessionCommand(
                request == null ? "" : request.fileName(),
                request == null ? "" : request.contentType(),
                request == null ? 0 : request.contentLength(),
                request == null ? "" : request.checksumSha256()
        );
    }

    private static AvatarUploadSessionResponse toAvatarUploadSessionResponse(AvatarUploadSessionResult session) {
        if (session == null) {
            return null;
        }
        return new AvatarUploadSessionResponse(
                session.uploadId(),
                session.objectId() == null ? "" : session.objectId().toString(),
                session.versionId() == null ? "" : session.versionId().toString(),
                new AvatarUploadSessionResponse.UploadInstruction(
                        session.uploadUrl(),
                        session.uploadMethod(),
                        session.fileField(),
                        session.fields(),
                        session.headers()
                ),
                new AvatarUploadSessionResponse.Constraints(session.maxBytes(), session.mimeTypes()),
                session.expiresAt() == null ? "" : session.expiresAt().toString()
        );
    }

}
