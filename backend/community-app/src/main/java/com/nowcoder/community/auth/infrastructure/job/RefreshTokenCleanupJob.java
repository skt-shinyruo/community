package com.nowcoder.community.auth.infrastructure.job;

import com.nowcoder.community.auth.application.RefreshTokenApplicationService;
import com.nowcoder.community.auth.config.RefreshTokenCleanupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenApplicationService refreshTokenApplicationService;
    private final RefreshTokenCleanupProperties properties;

    public RefreshTokenCleanupJob(
            RefreshTokenApplicationService refreshTokenApplicationService,
            RefreshTokenCleanupProperties properties
    ) {
        this.refreshTokenApplicationService = refreshTokenApplicationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${auth.refresh.cleanup.interval-ms:3600000}")
    public void cleanup() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            int deleted = refreshTokenApplicationService.cleanupExpiredBefore(Instant.now());
            if (deleted > 0) {
                log.info("[auth] cleaned up expired refresh tokens count={}", deleted);
            }
        } catch (RuntimeException e) {
            log.warn("[auth] refresh-token cleanup failed: {}", e.toString());
        }
    }
}
