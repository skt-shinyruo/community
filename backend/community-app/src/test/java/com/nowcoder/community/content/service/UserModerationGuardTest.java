package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserModerationGuardTest {

    @Test
    void constructorShouldUseUserModerationQueryApiForActiveBan() {
        UserModerationQueryApi userModerationQueryApi = mock(UserModerationQueryApi.class);
        UserModerationStateView status = new UserModerationStateView(7, null, Instant.now().plusSeconds(60));
        when(userModerationQueryApi.getModerationState(7)).thenReturn(status);
        UserModerationGuard guard = new UserModerationGuard(userModerationQueryApi);

        BusinessException ex = catchThrowableOfType(() -> guard.assertCanSpeak(7), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThat(ex.getMessage()).isEqualTo("账号已被封禁，无法发言");
        verify(userModerationQueryApi).getModerationState(7);
    }
}
