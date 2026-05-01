package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarFileResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFileApplicationServiceTest {

    @Mock
    private AvatarStoragePort avatarStoragePort;

    @Test
    void loadAvatarOrNullShouldValidateKeyAndDelegateToStoragePort() {
        UserFileApplicationService service = new UserFileApplicationService(avatarStoragePort);
        UUID userId = uuid(7);
        String key = "avatar/" + userId + "/0123456789abcdef0123456789abcdef";
        AvatarFileResult file = new AvatarFileResult(
                new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)),
                "text/plain",
                2
        );
        when(avatarStoragePort.loadAvatarOrNull(key)).thenReturn(file);

        AvatarFileResult result = service.loadAvatarOrNull("/files/" + key);

        assertThat(result).isEqualTo(file);
        verify(avatarStoragePort).loadAvatarOrNull(key);
    }

    @Test
    void loadAvatarOrNullShouldReturnNullWhenStorageMisses() {
        UserFileApplicationService service = new UserFileApplicationService(avatarStoragePort);
        UUID userId = uuid(7);
        String key = "avatar/" + userId + "/0123456789abcdef0123456789abcdef";
        when(avatarStoragePort.loadAvatarOrNull(key)).thenReturn(null);

        AvatarFileResult result = service.loadAvatarOrNull("/files/" + key);

        assertThat(result).isNull();
        verify(avatarStoragePort).loadAvatarOrNull(key);
    }

    @Test
    void loadAvatarOrNullShouldRejectInvalidFileKey() {
        UserFileApplicationService service = new UserFileApplicationService(avatarStoragePort);

        Throwable thrown = catchThrowable(() -> service.loadAvatarOrNull("/files/avatar/../secret"));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("fileKey 非法");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(INVALID_ARGUMENT);
        verifyNoInteractions(avatarStoragePort);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
