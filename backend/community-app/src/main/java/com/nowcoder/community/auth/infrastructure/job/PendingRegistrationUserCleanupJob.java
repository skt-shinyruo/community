package com.nowcoder.community.auth.infrastructure.job;

import com.nowcoder.community.auth.application.RegistrationApplicationService;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.common.trace.TraceJobRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingRegistrationUserCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PendingRegistrationUserCleanupJob.class);

    private final RegistrationApplicationService registrationApplicationService;
    private final RegistrationProperties properties;

    public PendingRegistrationUserCleanupJob(RegistrationApplicationService registrationApplicationService, RegistrationProperties properties) {
        this.registrationApplicationService = registrationApplicationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${auth.registration.pending-user.cleanup-interval-ms:300000}")
    public void cleanup() {
        TraceJobRunner.run("pending-registration-user-cleanup", () -> {
            if (!properties.getPendingUser().isLocalSchedulerEnabled()) {
                return;
            }
            try {
                int totalDeleted = registrationApplicationService.cleanupExpiredPendingUsers();
                if (totalDeleted > 0) {
                    log.info("[registration] cleaned up expired pending users count={}", totalDeleted);
                }
            } catch (RuntimeException e) {
                log.warn("[registration] pending-user cleanup failed: {}", e.toString());
            }
        });
    }
}
