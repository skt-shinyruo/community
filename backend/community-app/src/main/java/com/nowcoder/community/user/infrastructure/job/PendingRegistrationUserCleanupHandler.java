package com.nowcoder.community.user.infrastructure.job;

import com.nowcoder.community.user.application.UserRegistrationApplicationService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PendingRegistrationUserCleanupHandler {

    static final String JOB_NAME = "pendingRegistrationUserCleanup";

    private static final Logger log = LoggerFactory.getLogger(PendingRegistrationUserCleanupHandler.class);

    private final UserRegistrationApplicationService applicationService;
    private final long pendingUserTtlSeconds;

    public PendingRegistrationUserCleanupHandler(
            UserRegistrationApplicationService applicationService,
            @Value("${auth.registration.pending-user.ttl-seconds:1800}") long pendingUserTtlSeconds
    ) {
        this.applicationService = applicationService;
        this.pendingUserTtlSeconds = pendingUserTtlSeconds;
    }

    @XxlJob(JOB_NAME)
    public void cleanup() {
        try {
            Duration ttl = Duration.ofSeconds(Math.max(60L, pendingUserTtlSeconds));
            int deleted;
            int totalDeleted = 0;
            do {
                deleted = applicationService.cleanupExpiredPendingUsers(ttl);
                totalDeleted += deleted;
            } while (deleted > 0);
            String result = "[registration] pending-user cleanup deleted-count=" + totalDeleted;
            XxlJobHelper.log(result);
            XxlJobHelper.handleSuccess(result);
            if (totalDeleted > 0) {
                log.info("[registration] cleaned up expired pending users count={}", totalDeleted);
            }
        } catch (RuntimeException e) {
            String message = "[registration] pending-user cleanup failed: " + e;
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail(message);
            log.warn(message);
        }
    }
}
