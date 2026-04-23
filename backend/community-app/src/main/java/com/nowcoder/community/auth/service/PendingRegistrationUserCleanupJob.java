package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PendingRegistrationUserCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PendingRegistrationUserCleanupJob.class);

    private final UserRegistrationActionApi userRegistrationActionApi;
    private final RegistrationProperties properties;

    public PendingRegistrationUserCleanupJob(UserRegistrationActionApi userRegistrationActionApi, RegistrationProperties properties) {
        this.userRegistrationActionApi = userRegistrationActionApi;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${auth.registration.pending-user.cleanup-interval-ms:300000}")
    public void cleanup() {
        if (!properties.getPendingUser().isLocalSchedulerEnabled()) {
            return;
        }
        try {
            Duration ttl = Duration.ofSeconds(Math.max(60, properties.getPendingUser().getTtlSeconds()));
            int deleted;
            int totalDeleted = 0;
            do {
                deleted = userRegistrationActionApi.cleanupExpiredPendingUsers(ttl);
                totalDeleted += deleted;
            } while (deleted > 0);
            if (totalDeleted > 0) {
                log.info("[registration] cleaned up expired pending users count={}", totalDeleted);
            }
        } catch (RuntimeException e) {
            log.warn("[registration] pending-user cleanup failed: {}", e.toString());
        }
    }
}
