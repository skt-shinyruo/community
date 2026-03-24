package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RefreshTokenCleanupProperties;
import com.nowcoder.community.user.session.RefreshTokenSessionService;
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
        RefreshTokenSessionService refreshTokenSessionService = mock(RefreshTokenSessionService.class);
        RefreshTokenCleanupProperties properties = new RefreshTokenCleanupProperties();
        properties.setEnabled(false);

        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenSessionService, properties);
        job.cleanup();

        verifyNoInteractions(refreshTokenSessionService);
    }

    @Test
    void cleanupShouldDelegateToSessionServiceWhenEnabled() {
        RefreshTokenSessionService refreshTokenSessionService = mock(RefreshTokenSessionService.class);
        RefreshTokenCleanupProperties properties = new RefreshTokenCleanupProperties();
        properties.setEnabled(true);
        when(refreshTokenSessionService.deleteExpiredBefore(any(Instant.class))).thenReturn(2);

        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenSessionService, properties);
        job.cleanup();

        verify(refreshTokenSessionService, times(1)).deleteExpiredBefore(any(Instant.class));
        verifyNoMoreInteractions(refreshTokenSessionService);
    }

    @Test
    void cleanupShouldBeFailSafeWhenServiceThrowsRuntimeException() {
        RefreshTokenSessionService refreshTokenSessionService = mock(RefreshTokenSessionService.class);
        RefreshTokenCleanupProperties properties = new RefreshTokenCleanupProperties();
        properties.setEnabled(true);
        when(refreshTokenSessionService.deleteExpiredBefore(any(Instant.class))).thenThrow(new RuntimeException("boom"));

        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenSessionService, properties);

        assertDoesNotThrow(job::cleanup);
        verify(refreshTokenSessionService, times(1)).deleteExpiredBefore(any(Instant.class));
        verifyNoMoreInteractions(refreshTokenSessionService);
    }
}

