package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.user.service.InternalUserService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingRegistrationUserCleanupJobTest {

    @Test
    void cleanupShouldDoNothingWhenLocalSchedulerDisabled() {
        InternalUserService internalUserService = mock(InternalUserService.class);
        RegistrationProperties properties = new RegistrationProperties();
        properties.getPendingUser().setLocalSchedulerEnabled(false);

        PendingRegistrationUserCleanupJob job = new PendingRegistrationUserCleanupJob(internalUserService, properties);
        job.cleanup();

        verifyNoInteractions(internalUserService);
    }

    @Test
    void cleanupShouldDelegateToInternalUserServiceWithConfiguredTtlWhenLocalSchedulerEnabled() {
        InternalUserService internalUserService = mock(InternalUserService.class);
        RegistrationProperties properties = new RegistrationProperties();
        properties.getPendingUser().setTtlSeconds(1800);
        properties.getPendingUser().setLocalSchedulerEnabled(true);
        when(internalUserService.cleanupExpiredPendingUsers(Duration.ofMinutes(30))).thenReturn(2);

        PendingRegistrationUserCleanupJob job = new PendingRegistrationUserCleanupJob(internalUserService, properties);
        job.cleanup();

        verify(internalUserService, times(1)).cleanupExpiredPendingUsers(Duration.ofMinutes(30));
        verifyNoMoreInteractions(internalUserService);
    }
}
