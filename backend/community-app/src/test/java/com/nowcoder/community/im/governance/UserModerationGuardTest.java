package com.nowcoder.community.im.governance;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserModerationGuardTest {

    @Test
    void constructorShouldUseUserModerationQueryApiForActiveMute() {
        UUID userId = uuid(7);
        UserModerationQueryApi userModerationQueryApi = mock(UserModerationQueryApi.class);
        UserModerationStateView status = new UserModerationStateView(userId, Instant.now().plusSeconds(60), null);
        when(userModerationQueryApi.getModerationState(userId)).thenReturn(status);
        UserModerationGuard guard = new UserModerationGuard(userModerationQueryApi);

        BusinessException ex = catchThrowableOfType(() -> guard.assertCanSendMessage(userId), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThat(ex.getMessage()).isEqualTo("你已被禁言，暂时无法发送私信");
        verify(userModerationQueryApi).getModerationState(userId);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
