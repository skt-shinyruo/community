package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RefreshTokenCleanupProperties;
import com.nowcoder.community.user.session.RefreshTokenSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenSessionService refreshTokenSessionService;
    private final RefreshTokenCleanupProperties properties;

    public RefreshTokenCleanupJob(RefreshTokenSessionService refreshTokenSessionService,
                                 RefreshTokenCleanupProperties properties) {
        this.refreshTokenSessionService = refreshTokenSessionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${auth.refresh.cleanup.interval-ms:3600000}")
    public void cleanup() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            int deleted = refreshTokenSessionService.deleteExpiredBefore(Instant.now());
            if (deleted > 0) {
                log.info("[auth] cleaned up expired refresh tokens count={}", deleted);
            }
        } catch (RuntimeException e) {
            log.warn("[auth] refresh-token cleanup failed: {}", e.toString());
        }
    }
}

