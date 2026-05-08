package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;
import com.nowcoder.community.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAvatarApplicationServiceTest {

    @Mock
    private AvatarStoragePort avatarStoragePort;

    @Mock
    private UserRepository userRepository;

    @Test
    void createUploadTokenShouldRejectNonSelfActor() {
        UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);

        Throwable thrown = catchThrowable(() -> service.createUploadToken(actorUserId, targetUserId));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("只能操作自己的头像");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(FORBIDDEN);
        verifyNoInteractions(avatarStoragePort, userRepository);
    }

    @Test
    void createUploadTokenShouldDelegateToAvatarStoragePort() {
        UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
        UUID userId = uuid(7);
        AvatarUploadTokenResult token = new AvatarUploadTokenResult(
                "oss",
                "upload-token",
                "avatar/" + userId + "/0123456789abcdef0123456789abcdef",
                "https://bucket.example/avatar",
                "/api/users/" + userId + "/avatar/upload",
                "POST",
                2_097_152L,
                "image/png;image/jpeg"
        );
        when(avatarStoragePort.createUploadToken(userId)).thenReturn(token);

        AvatarUploadTokenResult result = service.createUploadToken(userId, userId);

        assertThat(result).isEqualTo(token);
        verify(avatarStoragePort).createUploadToken(userId);
        verifyNoInteractions(userRepository);
    }

    @Test
    void uploadShouldRejectNonSelfActor() {
        UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);
        String fileName = "avatar/" + targetUserId + "/0123456789abcdef0123456789abcdef";
        AvatarUploadContent content = uploadContent();

        Throwable thrown = catchThrowable(() -> service.upload(actorUserId, targetUserId, fileName, content));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("只能操作自己的头像");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(FORBIDDEN);
        verifyNoInteractions(avatarStoragePort, userRepository);
    }

    @Test
    void uploadShouldDelegateToAvatarStoragePort() {
        UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
        UUID userId = uuid(7);
        String fileName = "avatar/" + userId + "/0123456789abcdef0123456789abcdef";
        AvatarUploadContent content = uploadContent();

        service.upload(userId, userId, fileName, content);

        verify(avatarStoragePort).upload(userId, fileName, content);
        verifyNoInteractions(userRepository);
    }

    @Test
    void updateAvatarShouldConsumeTicketBuildUrlAndUpdateUserHeaderUrl() {
        UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
        UUID userId = uuid(7);
        String fileName = "avatar/" + userId + "/0123456789abcdef0123456789abcdef";
        String headerUrl = "https://cdn.example.com/files/" + fileName;
        when(avatarStoragePort.buildAvatarUrl(fileName)).thenReturn(headerUrl);

        service.updateAvatar(userId, userId, fileName);

        InOrder inOrder = inOrder(avatarStoragePort, userRepository);
        inOrder.verify(avatarStoragePort).assertAndConsumeUploadTicket(userId, fileName);
        inOrder.verify(avatarStoragePort).buildAvatarUrl(fileName);
        inOrder.verify(userRepository).updateHeaderUrl(userId, headerUrl);
    }

    private static AvatarUploadContent uploadContent() {
        return new AvatarUploadContent(
                () -> new ByteArrayInputStream("avatar".getBytes(StandardCharsets.UTF_8)),
                "image/png",
                6,
                false
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
