package com.nowcoder.community.user.infrastructure.job;

import com.nowcoder.community.user.application.UserRegistrationApplicationService;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingRegistrationUserCleanupHandlerTest {

    @AfterEach
    void tearDown() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    void cleanupShouldDelegateToUserRegistrationServiceWithConfiguredTtlAndReportDeletedCount() {
        UserRegistrationApplicationService userRegistrationApplicationService =
                mock(UserRegistrationApplicationService.class);
        when(userRegistrationApplicationService.cleanupExpiredPendingUsers(Duration.ofMinutes(30))).thenReturn(500, 2, 0);

        PendingRegistrationUserCleanupHandler handler =
                new PendingRegistrationUserCleanupHandler(userRegistrationApplicationService, 1800);
        XxlJobContext context = new XxlJobContext(1L, "", 2L, System.currentTimeMillis(), "", 0, 1);
        XxlJobContext.setXxlJobContext(context);

        handler.cleanup();

        verify(userRegistrationApplicationService, times(3)).cleanupExpiredPendingUsers(Duration.ofMinutes(30));
        verifyNoMoreInteractions(userRegistrationApplicationService);
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
        assertThat(context.getHandleMsg()).contains("502");
    }

    @Test
    void cleanupShouldUsePendingRegistrationUserCleanupJobName() throws NoSuchMethodException {
        Method method = PendingRegistrationUserCleanupHandler.class.getDeclaredMethod("cleanup");

        XxlJob annotation = method.getAnnotation(XxlJob.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("pendingRegistrationUserCleanup");
    }

    @Test
    void cleanupShouldMarkFailureWithoutThrowingWhenCleanupFails() {
        UserRegistrationApplicationService userRegistrationApplicationService =
                mock(UserRegistrationApplicationService.class);
        when(userRegistrationApplicationService.cleanupExpiredPendingUsers(Duration.ofMinutes(30)))
                .thenThrow(new RuntimeException("boom"));

        PendingRegistrationUserCleanupHandler handler =
                new PendingRegistrationUserCleanupHandler(userRegistrationApplicationService, 1800);
        XxlJobContext context = new XxlJobContext(1L, "", 2L, System.currentTimeMillis(), "", 0, 1);
        XxlJobContext.setXxlJobContext(context);

        handler.cleanup();

        verify(userRegistrationApplicationService, times(1)).cleanupExpiredPendingUsers(Duration.ofMinutes(30));
        verifyNoMoreInteractions(userRegistrationApplicationService);
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
        assertThat(context.getHandleMsg()).contains("boom");
    }

    @Test
    void cleanupShouldClampConfiguredTtlToMinimumOneMinute() {
        UserRegistrationApplicationService userRegistrationApplicationService =
                mock(UserRegistrationApplicationService.class);
        when(userRegistrationApplicationService.cleanupExpiredPendingUsers(Duration.ofMinutes(1))).thenReturn(0);

        PendingRegistrationUserCleanupHandler handler =
                new PendingRegistrationUserCleanupHandler(userRegistrationApplicationService, 1);
        XxlJobContext context = new XxlJobContext(1L, "", 2L, System.currentTimeMillis(), "", 0, 1);
        XxlJobContext.setXxlJobContext(context);

        handler.cleanup();

        verify(userRegistrationApplicationService, times(1)).cleanupExpiredPendingUsers(Duration.ofMinutes(1));
        verifyNoMoreInteractions(userRegistrationApplicationService);
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    }
}
