package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.user.app.query.GetUserProfilePageQuery;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.dto.AvatarUploadTokenResponse;
import com.nowcoder.community.user.dto.UpdateAvatarRequest;
import com.nowcoder.community.user.service.AvatarService;
import com.nowcoder.community.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class UserControllerLoggingTest {

    private AvatarService avatarService;
    private UserService userService;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        avatarService = mock(AvatarService.class);
        controller = new UserController(
                mock(UserLookupQueryApi.class),
                mock(GetUserProfilePageQuery.class),
                userService,
                avatarService
        );
    }

    @Test
    void uploadTokenShouldLogSecurityEventWithoutUploadTokenMaterial(CapturedOutput output) {
        AvatarUploadTokenResponse response = new AvatarUploadTokenResponse();
        response.setProvider("r2");
        response.setFileName("avatar/42/abc123");
        response.setUploadToken("secret-upload-token");
        when(avatarService.createUploadToken(42)).thenReturn(response);

        Result<AvatarUploadTokenResponse> result = controller.uploadToken(authentication(42), 42);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isSameAs(response);
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=avatar_upload_token")
                .contains("community.outcome=success")
                .contains("user.id=42")
                .contains("community.target_type=user")
                .contains("community.target_id=42")
                .contains("community.avatar_provider=r2")
                .doesNotContain("secret-upload-token");
    }

    @Test
    void uploadAvatarShouldLogSecurityEventWithoutFileContent(CapturedOutput output) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "fake-image-bytes".getBytes()
        );

        Result<Void> result = controller.uploadAvatar(authentication(42), 42, file, "avatar/42/abc123");

        assertThat(result.getCode()).isEqualTo(0);
        verify(avatarService).upload(42, "avatar/42/abc123", file);
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=avatar_upload")
                .contains("community.outcome=success")
                .contains("user.id=42")
                .contains("community.target_type=user")
                .contains("community.target_id=42")
                .contains("community.avatar_file_name=avatar/42/abc123")
                .contains("community.file_content_type=image/png")
                .contains("community.file_size_bytes=16")
                .doesNotContain("fake-image-bytes");
    }

    @Test
    void updateAvatarShouldLogSecurityEventWithoutAvatarUrl(CapturedOutput output) {
        UpdateAvatarRequest request = new UpdateAvatarRequest();
        request.setFileName("avatar/42/abc123");
        when(avatarService.buildAvatarUrl("avatar/42/abc123")).thenReturn("https://cdn.example.com/avatar/42/abc123");

        Result<Void> result = controller.updateAvatar(authentication(42), 42, request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(avatarService).assertAndConsumeUploadTicket(42, "avatar/42/abc123");
        verify(userService).updateHeaderUrl(42, "https://cdn.example.com/avatar/42/abc123");
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=avatar_update")
                .contains("community.outcome=success")
                .contains("user.id=42")
                .contains("community.target_type=user")
                .contains("community.target_id=42")
                .contains("community.avatar_file_name=avatar/42/abc123")
                .doesNotContain("https://cdn.example.com/avatar/42/abc123");
    }

    private Authentication authentication(int userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .build();
        return new TestingAuthenticationToken(jwt, null);
    }
}
