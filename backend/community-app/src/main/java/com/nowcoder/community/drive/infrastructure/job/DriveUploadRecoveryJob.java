package com.nowcoder.community.drive.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.drive.application.DriveUploadApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
public class DriveUploadRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(DriveUploadRecoveryJob.class);
    private static final String JOB_NAME = "drive-upload-recovery";

    private final DriveUploadApplicationService uploadApplicationService;
    private final Clock clock;
    private final boolean enabled;
    private final int batchSize;
    private final Duration staleAge;

    public DriveUploadRecoveryJob(
            DriveUploadApplicationService uploadApplicationService,
            Clock clock,
            @Value("${drive.upload.recovery.enabled:true}") boolean enabled,
            @Value("${drive.upload.recovery.batch-size:100}") int batchSize,
            @Value("${drive.upload.recovery.stale-seconds:300}") long staleSeconds
    ) {
        this.uploadApplicationService = Objects.requireNonNull(
                uploadApplicationService,
                "uploadApplicationService must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(1000, batchSize));
        this.staleAge = Duration.ofSeconds(Math.max(30L, staleSeconds));
    }

    @Scheduled(fixedDelayString = "${drive.upload.recovery.delay-ms:60000}")
    public void recover() {
        TraceJobRunner.run(JOB_NAME, () -> {
            if (!enabled) {
                return;
            }
            try {
                Instant updatedBefore = clock.instant().minus(staleAge);
                DriveUploadApplicationService.RecoveryResult result =
                        uploadApplicationService.recoverStaleUploads(updatedBefore, batchSize);
                if (result.prepared() > 0 || result.finalized() > 0 || result.markedObjectCompleted() > 0
                        || result.failed() > 0 || result.skipped() > 0) {
                    log.info(
                            "[drive-upload] recovery prepared={} finalized={} markedObjectCompleted={} failed={} skipped={}",
                            result.prepared(),
                            result.finalized(),
                            result.markedObjectCompleted(),
                            result.failed(),
                            result.skipped()
                    );
                }
            } catch (RuntimeException e) {
                log.warn("[drive-upload] recovery failed: {}", e.toString());
            }
        });
    }
}
