package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
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
        UserRegistrationActionApi userRegistrationActionApi = mock(UserRegistrationActionApi.class);
        RegistrationProperties properties = new RegistrationProperties();
        properties.getPendingUser().setLocalSchedulerEnabled(false);

        PendingRegistrationUserCleanupJob job = new PendingRegistrationUserCleanupJob(userRegistrationActionApi, properties);
        job.cleanup();

        verifyNoInteractions(userRegistrationActionApi);
    }

    @Test
    void cleanupShouldDelegateToUserRegistrationServiceWithConfiguredTtlWhenLocalSchedulerEnabled() {
        UserRegistrationActionApi userRegistrationActionApi = mock(UserRegistrationActionApi.class);
        RegistrationProperties properties = new RegistrationProperties();
        properties.getPendingUser().setTtlSeconds(1800);
        properties.getPendingUser().setLocalSchedulerEnabled(true);
        when(userRegistrationActionApi.cleanupExpiredPendingUsers(Duration.ofMinutes(30))).thenReturn(500, 2);

        PendingRegistrationUserCleanupJob job = new PendingRegistrationUserCleanupJob(userRegistrationActionApi, properties);
        job.cleanup();

        verify(userRegistrationActionApi, times(2)).cleanupExpiredPendingUsers(Duration.ofMinutes(30));
        verifyNoMoreInteractions(userRegistrationActionApi);
    }
}
