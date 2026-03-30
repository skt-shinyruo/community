package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RefreshTokenCleanupProperties;
import com.nowcoder.community.user.api.action.UserRefreshTokenSessionActionApi;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RefreshTokenCleanupJobTest {

    @Test
    void cleanupShouldDoNothingWhenDisabled() {
        UserRefreshTokenSessionActionApi refreshTokenSessionActionApi = mock(UserRefreshTokenSessionActionApi.class);
        RefreshTokenCleanupProperties properties = new RefreshTokenCleanupProperties();
        properties.setEnabled(false);

        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenSessionActionApi, properties);
        job.cleanup();

        verifyNoInteractions(refreshTokenSessionActionApi);
    }

    @Test
    void cleanupShouldDelegateToSessionServiceWhenEnabled() {
        UserRefreshTokenSessionActionApi refreshTokenSessionActionApi = mock(UserRefreshTokenSessionActionApi.class);
        RefreshTokenCleanupProperties properties = new RefreshTokenCleanupProperties();
        properties.setEnabled(true);
        when(refreshTokenSessionActionApi.deleteExpiredBefore(any(Instant.class))).thenReturn(2);

        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenSessionActionApi, properties);
        job.cleanup();

        verify(refreshTokenSessionActionApi, times(1)).deleteExpiredBefore(any(Instant.class));
        verifyNoMoreInteractions(refreshTokenSessionActionApi);
    }

    @Test
    void cleanupShouldBeFailSafeWhenServiceThrowsRuntimeException() {
        UserRefreshTokenSessionActionApi refreshTokenSessionActionApi = mock(UserRefreshTokenSessionActionApi.class);
        RefreshTokenCleanupProperties properties = new RefreshTokenCleanupProperties();
        properties.setEnabled(true);
        when(refreshTokenSessionActionApi.deleteExpiredBefore(any(Instant.class))).thenThrow(new RuntimeException("boom"));

        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenSessionActionApi, properties);

        assertDoesNotThrow(job::cleanup);
        verify(refreshTokenSessionActionApi, times(1)).deleteExpiredBefore(any(Instant.class));
        verifyNoMoreInteractions(refreshTokenSessionActionApi);
    }
}
