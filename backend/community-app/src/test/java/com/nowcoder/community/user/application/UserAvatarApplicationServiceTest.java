package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.command.CreateAvatarUploadSessionCommand;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarUploadSessionResult;
import com.nowcoder.community.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    void createUploadSessionShouldRejectNonSelfActor() {
        UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);

        Throwable thrown = catchThrowable(() -> service.createUploadSession(actorUserId, targetUserId, uploadSessionCommand()));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("只能操作自己的头像");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(FORBIDDEN);
        verifyNoInteractions(avatarStoragePort, userRepository);
    }

    @Test
    void createUploadSessionShouldDelegateToAvatarStoragePort() {
        UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
        UUID userId = uuid(7);
        UUID objectId = uuid(21);
        UUID versionId = uuid(22);
        CreateAvatarUploadSessionCommand command = uploadSessionCommand();
        AvatarUploadSessionResult session = new AvatarUploadSessionResult(
                "upload-session-id",
                objectId,
                versionId,
                "/api/oss/objects/" + objectId + "/complete",
                "POST",
                "file",
                Map.of("sessionId", "upload-session-id", "versionId", versionId.toString()),
                Map.of(),
                2_097_152L,
                List.of("image/png", "image/jpeg"),
                Instant.parse("2026-05-08T12:00:00Z")
        );
        when(avatarStoragePort.createUploadSession(userId, command)).thenReturn(session);

        AvatarUploadSessionResult result = service.createUploadSession(userId, userId, command);

        assertThat(result).isEqualTo(session);
        verify(avatarStoragePort).createUploadSession(userId, command);
        verifyNoInteractions(userRepository);
    }

    @Test
    void updateAvatarShouldRejectNonSelfActor() {
        UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);
        UUID objectId = uuid(30);

        Throwable thrown = catchThrowable(() -> service.updateAvatar(actorUserId, targetUserId, objectId));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("只能操作自己的头像");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(FORBIDDEN);
        verifyNoInteractions(avatarStoragePort, userRepository);
    }

    @Test
    void updateAvatarShouldResolveObjectUrlAndUpdateUserHeaderUrl() {
        UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
        UUID userId = uuid(7);
        UUID objectId = uuid(30);
        String headerUrl = "https://cdn.example.com/files/" + objectId + "/avatar.png";
        when(avatarStoragePort.resolvePublicAvatarUrl(userId, objectId)).thenReturn(headerUrl);

        service.updateAvatar(userId, userId, objectId);

        InOrder inOrder = inOrder(avatarStoragePort, userRepository);
        inOrder.verify(avatarStoragePort).resolvePublicAvatarUrl(userId, objectId);
        inOrder.verify(userRepository).updateHeaderUrl(userId, headerUrl);
    }

    private static CreateAvatarUploadSessionCommand uploadSessionCommand() {
        return new CreateAvatarUploadSessionCommand("avatar.png", "image/png", 6, "sha256-avatar");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
