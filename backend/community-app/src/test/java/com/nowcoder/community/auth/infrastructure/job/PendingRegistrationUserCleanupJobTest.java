package com.nowcoder.community.auth.infrastructure.job;

import com.nowcoder.community.auth.application.RegistrationApplicationService;
import com.nowcoder.community.auth.config.RegistrationProperties;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingRegistrationUserCleanupJobTest {

    @Test
    void cleanupShouldDoNothingWhenLocalSchedulerDisabled() {
        RegistrationApplicationService registrationApplicationService = mock(RegistrationApplicationService.class);
        RegistrationProperties properties = new RegistrationProperties();
        properties.getPendingUser().setLocalSchedulerEnabled(false);

        PendingRegistrationUserCleanupJob job = new PendingRegistrationUserCleanupJob(registrationApplicationService, properties);
        job.cleanup();

        verifyNoInteractions(registrationApplicationService);
    }

    @Test
    void cleanupShouldDelegateToUserRegistrationServiceWithConfiguredTtlWhenLocalSchedulerEnabled() {
        RegistrationApplicationService registrationApplicationService = mock(RegistrationApplicationService.class);
        RegistrationProperties properties = new RegistrationProperties();
        properties.getPendingUser().setTtlSeconds(1800);
        properties.getPendingUser().setLocalSchedulerEnabled(true);
        when(registrationApplicationService.cleanupExpiredPendingUsers()).thenReturn(502);

        PendingRegistrationUserCleanupJob job = new PendingRegistrationUserCleanupJob(registrationApplicationService, properties);
        job.cleanup();

        verify(registrationApplicationService, times(1)).cleanupExpiredPendingUsers();
        verifyNoMoreInteractions(registrationApplicationService);
    }
}
