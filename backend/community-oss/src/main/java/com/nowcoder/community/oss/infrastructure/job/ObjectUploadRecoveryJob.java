package com.nowcoder.community.oss.infrastructure.job;

import com.nowcoder.community.oss.application.ObjectUploadRecoveryApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class ObjectUploadRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(ObjectUploadRecoveryJob.class);
    private static final Duration MIN_UPLOAD_STALE_AGE = Duration.ofMinutes(35);

    private final ObjectUploadRecoveryApplicationService applicationService;
    private final Clock clock;
    private final boolean enabled;
    private final int batchSize;
    private final Duration uploadStaleAge;
    private final Duration cancellationStaleAge;

    public ObjectUploadRecoveryJob(
            ObjectUploadRecoveryApplicationService applicationService,
            Clock clock,
            @Value("${community.oss.upload-recovery.enabled:true}") boolean enabled,
            @Value("${community.oss.upload-recovery.batch-size:100}") int batchSize,
            @Value("${community.oss.upload-recovery.stale-seconds:2100}") long staleSeconds,
            @Value("${community.oss.upload-recovery.cancellation-stale-seconds:300}")
            long cancellationStaleSeconds
    ) {
        this.applicationService = applicationService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(500, batchSize));
        this.uploadStaleAge = Duration.ofSeconds(Math.max(MIN_UPLOAD_STALE_AGE.toSeconds(), staleSeconds));
        this.cancellationStaleAge = Duration.ofSeconds(Math.max(30L, cancellationStaleSeconds));
    }

    @Scheduled(fixedDelayString = "${community.oss.upload-recovery.delay-ms:60000}")
    public void recover() {
        if (!enabled) {
            return;
        }
        try {
            Instant now = clock.instant();
            applicationService.recoverStaleUploads(
                    now.minus(uploadStaleAge),
                    now.minus(cancellationStaleAge),
                    batchSize
            );
        } catch (RuntimeException exception) {
            log.warn("[oss-upload] recovery failed: {}", exception.toString());
        }
    }
}
