package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.content.application.PostMediaUploadRecoveryApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;

@Component
public class PostMediaUploadRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(PostMediaUploadRecoveryJob.class);

    private final PostMediaUploadRecoveryApplicationService applicationService;
    private final Clock clock;
    private final boolean enabled;
    private final int batchSize;
    private final Duration staleAge;

    public PostMediaUploadRecoveryJob(
            PostMediaUploadRecoveryApplicationService applicationService,
            Clock clock,
            @Value("${content.media.upload-recovery.enabled:false}") boolean enabled,
            @Value("${content.media.upload-recovery.batch-size:50}") int batchSize,
            @Value("${content.media.upload-recovery.stale-seconds:300}") long staleSeconds
    ) {
        this.applicationService = applicationService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(500, batchSize));
        this.staleAge = Duration.ofSeconds(Math.max(30L, staleSeconds));
    }

    @Scheduled(fixedDelayString = "${content.media.upload-recovery.delay-ms:60000}")
    public void recover() {
        if (!enabled) {
            return;
        }
        try {
            Date updatedBefore = Date.from(clock.instant().minus(staleAge));
            applicationService.recoverStaleCompleting(updatedBefore, batchSize);
        } catch (RuntimeException exception) {
            log.warn("[content-media-upload] recovery failed: {}", exception.toString());
        }
    }
}
