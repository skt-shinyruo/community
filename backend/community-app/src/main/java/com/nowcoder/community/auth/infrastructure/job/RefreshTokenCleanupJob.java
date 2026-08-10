package com.nowcoder.community.auth.infrastructure.job;

import com.nowcoder.community.auth.application.RefreshTokenApplicationService;
import com.nowcoder.community.auth.config.RefreshTokenCleanupProperties;
import com.nowcoder.community.common.trace.TraceJobRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;

@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenApplicationService refreshTokenApplicationService;
    private final RefreshTokenCleanupProperties properties;
    private final Clock clock;

    public RefreshTokenCleanupJob(
            RefreshTokenApplicationService refreshTokenApplicationService,
            RefreshTokenCleanupProperties properties,
            Clock clock
    ) {
        this.refreshTokenApplicationService = Objects.requireNonNull(
                refreshTokenApplicationService, "refreshTokenApplicationService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Scheduled(fixedDelayString = "${auth.refresh.cleanup.interval-ms:3600000}")
    public void cleanup() {
        TraceJobRunner.run("refresh-token-cleanup", () -> {
            if (!properties.isEnabled()) {
                return;
            }
            try {
                int deleted = refreshTokenApplicationService.cleanupExpiredBefore(clock.instant());
                if (deleted > 0) {
                    log.info("[auth] cleaned up expired refresh tokens count={}", deleted);
                }
            } catch (RuntimeException e) {
                log.warn("[auth] refresh-token cleanup failed: {}", e.toString());
            }
        });
    }
}
