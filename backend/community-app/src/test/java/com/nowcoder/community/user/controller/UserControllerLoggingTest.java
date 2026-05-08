package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.user.application.AvatarUploadContent;
import com.nowcoder.community.user.application.UserAvatarApplicationService;
import com.nowcoder.community.user.application.UserProfileApplicationService;
import com.nowcoder.community.user.application.UserReadApplicationService;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.controller.dto.AvatarUploadTokenResponse;
import com.nowcoder.community.user.controller.dto.UpdateAvatarRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
                mock(UserProfileApplicationService.class),
                new UserAvatarApplicationService(avatarStoragePort, userRepository)
        );
    }

    @Test
    void uploadTokenShouldLogSecurityEventWithoutUploadTokenMaterial(CapturedOutput output) {
        UUID userId = uuid(42);
        String fileName = "avatar/" + userId + "/0123456789abcdef0123456789abcdef";
        when(avatarStoragePort.createUploadToken(userId))
                .thenReturn(new AvatarUploadTokenResult(
                        "oss",
                        "secret-upload-token",
                        fileName,
                        null,
                        "/api/users/" + userId + "/avatar/upload",
                        "POST",
                        2_097_152L,
                        "image/png;image/jpeg"
                ));

        Result<AvatarUploadTokenResponse> result = controller.uploadToken(authentication(userId), userId);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getProvider()).isEqualTo("oss");
        assertThat(result.getData().getFileName()).isEqualTo(fileName);
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=avatar_upload_token")
                .contains("community.outcome=success")
                .contains("user.id=" + userId)
                .contains("community.target_type=user")
                .contains("community.target_id=" + userId)
                .contains("community.avatar_provider=oss")
                .doesNotContain("secret-upload-token");
    }

    @Test
    void uploadAvatarShouldLogSecurityEventWithoutFileContent(CapturedOutput output) throws Exception {
        UUID userId = uuid(42);
        String fileName = "avatar/" + userId + "/0123456789abcdef0123456789abcdef";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "fake-image-bytes".getBytes()
        );

        Result<Void> result = controller.uploadAvatar(authentication(userId), userId, file, fileName);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<AvatarUploadContent> contentCaptor = ArgumentCaptor.forClass(AvatarUploadContent.class);
        verify(avatarStoragePort).upload(eq(userId), eq(fileName), contentCaptor.capture());
        AvatarUploadContent content = contentCaptor.getValue();
        assertThat(content.contentType()).isEqualTo("image/png");
        assertThat(content.size()).isEqualTo(16);
        assertThat(content.empty()).isFalse();
        assertThat(content.openStream().readAllBytes()).isEqualTo("fake-image-bytes".getBytes());
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=avatar_upload")
                .contains("community.outcome=success")
                .contains("user.id=" + userId)
                .contains("community.target_type=user")
                .contains("community.target_id=" + userId)
                .contains("community.avatar_file_name=" + fileName)
                .contains("community.file_content_type=image/png")
                .contains("community.file_size_bytes=16")
                .doesNotContain("fake-image-bytes");
    }

    @Test
    void updateAvatarShouldLogSecurityEventWithoutAvatarUrl(CapturedOutput output) {
        UUID userId = uuid(42);
        String fileName = "avatar/" + userId + "/0123456789abcdef0123456789abcdef";
        UpdateAvatarRequest request = new UpdateAvatarRequest();
        request.setFileName(fileName);
        when(avatarStoragePort.buildAvatarUrl(fileName)).thenReturn("https://cdn.example.com/" + fileName);

        Result<Void> result = controller.updateAvatar(authentication(userId), userId, request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(avatarStoragePort).assertAndConsumeUploadTicket(userId, fileName);
        verify(userRepository).updateHeaderUrl(userId, "https://cdn.example.com/" + fileName);
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=avatar_update")
                .contains("community.outcome=success")
                .contains("user.id=" + userId)
                .contains("community.target_type=user")
                .contains("community.target_id=" + userId)
                .contains("community.avatar_file_name=" + fileName)
                .doesNotContain("https://cdn.example.com/" + fileName);
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
