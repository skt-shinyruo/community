package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.content.application.CommentThreadCleanupApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CommentThreadCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CommentThreadCleanupJob.class);

    private final CommentThreadCleanupApplicationService applicationService;
    private final boolean enabled;
    private final int batchSize;

    public CommentThreadCleanupJob(
            CommentThreadCleanupApplicationService applicationService,
            @Value("${content.comment-thread-cleanup.enabled:true}") boolean enabled,
            @Value("${content.comment-thread-cleanup.batch-size:100}") int batchSize
    ) {
        this.applicationService = applicationService;
        this.enabled = enabled;
        this.batchSize = Math.min(500, Math.max(1, batchSize));
    }

    @Scheduled(
            initialDelayString = "${content.comment-thread-cleanup.delay-ms:60000}",
            fixedDelayString = "${content.comment-thread-cleanup.delay-ms:60000}"
    )
    public void cleanup() {
        TraceJobRunner.run("comment-thread-cleanup", () -> {
            if (!enabled) {
                return;
            }
            try {
                CommentThreadCleanupApplicationService.CleanupResult result =
                        applicationService.reconcile(batchSize);
                if (result.failed() > 0) {
                    log.warn("[content-comment] cleanup scanned={} completed={} failed={}",
                            result.scanned(), result.completed(), result.failed());
                }
            } catch (RuntimeException exception) {
                log.warn("[content-comment] cleanup scan failed: {}", exception.toString());
            }
        });
    }
}
