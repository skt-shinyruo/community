package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.user.application.UserAvatarApplicationService;
import com.nowcoder.community.user.application.UserReadApplicationService;
import com.nowcoder.community.user.application.command.CreateAvatarUploadSessionCommand;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarUploadSessionResult;
import com.nowcoder.community.user.controller.dto.AvatarUploadSessionRequest;
import com.nowcoder.community.user.controller.dto.AvatarUploadSessionResponse;
import com.nowcoder.community.user.controller.dto.UpdateAvatarRequest;
import com.nowcoder.community.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class UserControllerLoggingTest {

    private AvatarStoragePort avatarStoragePort;
    private UserRepository userRepository;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        avatarStoragePort = mock(AvatarStoragePort.class);
        controller = new UserController(
                mock(UserReadApplicationService.class),
                new UserAvatarApplicationService(avatarStoragePort, userRepository)
        );
    }

    @Test
    void uploadSessionShouldLogSecurityEventWithoutSessionSecret(CapturedOutput output) {
        UUID userId = uuid(42);
        UUID objectId = uuid(50);
        UUID versionId = uuid(51);
        CreateAvatarUploadSessionCommand command = new CreateAvatarUploadSessionCommand("avatar.png", "image/png", 16, "");
        when(avatarStoragePort.createUploadSession(userId, command))
                .thenReturn(new AvatarUploadSessionResult(
                        "secret-upload-session",
                        objectId,
                        versionId,
                        "/api/oss/objects/" + objectId + "/complete",
                        "POST",
                        "file",
                        Map.of("sessionId", "secret-upload-session", "versionId", versionId.toString()),
                        Map.of(),
                        2_097_152L,
                        List.of("image/png", "image/jpeg"),
                        Instant.parse("2026-05-08T12:00:00Z")
                ));
        AvatarUploadSessionRequest request = new AvatarUploadSessionRequest();
        request.setFileName("avatar.png");
        request.setContentType("image/png");
        request.setContentLength(16);

        Result<AvatarUploadSessionResponse> result = controller.createAvatarUploadSession(authentication(userId), userId, request);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getUploadId()).isEqualTo("secret-upload-session");
        assertThat(result.getData().getObjectId()).isEqualTo(objectId.toString());
        assertThat(result.getData().getVersionId()).isEqualTo(versionId.toString());
        assertThat(result.getData().getUpload()).isNotNull();
        assertThat(result.getData().getUpload().getUrl()).isEqualTo("/api/oss/objects/" + objectId + "/complete");
        assertThat(output.getAll())
                .contains("user.id=" + userId)
                .contains("community.target_type=user")
                .contains("community.target_id=" + userId)
                .contains("community.avatar_object_id=" + objectId)
                .doesNotContain("community.avatar_provider")
                .doesNotContain("secret-upload-session");
    }

    @Test
    void updateAvatarShouldLogSecurityEventWithoutAvatarUrl(CapturedOutput output) {
        UUID userId = uuid(42);
        UUID objectId = uuid(50);
        UpdateAvatarRequest request = new UpdateAvatarRequest();
        request.setObjectId(objectId);
        when(avatarStoragePort.resolvePublicAvatarUrl(userId, objectId))
                .thenReturn("https://cdn.example.com/files/" + objectId + "/avatar.png");

        Result<Void> result = controller.updateAvatar(authentication(userId), userId, request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(avatarStoragePort).resolvePublicAvatarUrl(userId, objectId);
        verify(userRepository).updateHeaderUrl(userId, "https://cdn.example.com/files/" + objectId + "/avatar.png");
        assertThat(output.getAll())
                .contains("user.id=" + userId)
                .contains("community.target_type=user")
                .contains("community.target_id=" + userId)
                .contains("community.avatar_object_id=" + objectId)
                .doesNotContain("https://cdn.example.com/files/" + objectId + "/avatar.png");
    }

    private Authentication authentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(userId.toString())
                .build();
        return new TestingAuthenticationToken(jwt, null);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
