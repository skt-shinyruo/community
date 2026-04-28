package com.nowcoder.community.auth.infrastructure.job;

import com.nowcoder.community.auth.application.RefreshTokenApplicationService;
import com.nowcoder.community.auth.config.RefreshTokenCleanupProperties;
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
        RefreshTokenApplicationService refreshTokenApplicationService = mock(RefreshTokenApplicationService.class);
        RefreshTokenCleanupProperties properties = new RefreshTokenCleanupProperties();
        properties.setEnabled(false);

        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenApplicationService, properties);
        job.cleanup();

        verifyNoInteractions(refreshTokenApplicationService);
    }

    @Test
    void cleanupShouldDelegateToSessionServiceWhenEnabled() {
        RefreshTokenApplicationService refreshTokenApplicationService = mock(RefreshTokenApplicationService.class);
        RefreshTokenCleanupProperties properties = new RefreshTokenCleanupProperties();
        properties.setEnabled(true);
        when(refreshTokenApplicationService.cleanupExpiredBefore(any(Instant.class))).thenReturn(2);

        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenApplicationService, properties);
        job.cleanup();

        verify(refreshTokenApplicationService, times(1)).cleanupExpiredBefore(any(Instant.class));
        verifyNoMoreInteractions(refreshTokenApplicationService);
    }

    @Test
    void cleanupShouldBeFailSafeWhenServiceThrowsRuntimeException() {
        RefreshTokenApplicationService refreshTokenApplicationService = mock(RefreshTokenApplicationService.class);
        RefreshTokenCleanupProperties properties = new RefreshTokenCleanupProperties();
        properties.setEnabled(true);
        when(refreshTokenApplicationService.cleanupExpiredBefore(any(Instant.class))).thenThrow(new RuntimeException("boom"));

        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenApplicationService, properties);

        assertDoesNotThrow(job::cleanup);
        verify(refreshTokenApplicationService, times(1)).cleanupExpiredBefore(any(Instant.class));
        verifyNoMoreInteractions(refreshTokenApplicationService);
    }
}
