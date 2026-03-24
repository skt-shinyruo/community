package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.user.service.InternalUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PendingRegistrationUserCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PendingRegistrationUserCleanupJob.class);

    private final InternalUserService internalUserService;
    private final RegistrationProperties properties;

    public PendingRegistrationUserCleanupJob(InternalUserService internalUserService, RegistrationProperties properties) {
        this.internalUserService = internalUserService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${auth.registration.pending-user.cleanup-interval-ms:300000}")
    public void cleanup() {
        if (!properties.getPendingUser().isLocalSchedulerEnabled()) {
            return;
        }
        try {
            Duration ttl = Duration.ofSeconds(Math.max(60, properties.getPendingUser().getTtlSeconds()));
            int deleted = internalUserService.cleanupExpiredPendingUsers(ttl);
            if (deleted > 0) {
                log.info("[registration] cleaned up expired pending users count={}", deleted);
            }
        } catch (RuntimeException e) {
            log.warn("[registration] pending-user cleanup failed: {}", e.toString());
        }
    }
}
