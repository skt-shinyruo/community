package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.content.application.PostMediaReferenceReconciliationApplicationService;
import com.nowcoder.community.content.application.command.ReconcilePostMediaReferencesCommand;
import com.nowcoder.community.content.application.result.PostMediaReferenceReconciliationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PostMediaReferenceReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PostMediaReferenceReconciliationJob.class);
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final PostMediaReferenceReconciliationApplicationService applicationService;
    private final boolean enabled;
    private final int batchSize;
    private UUID cursor = ZERO_UUID;

    public PostMediaReferenceReconciliationJob(
            PostMediaReferenceReconciliationApplicationService applicationService,
            @Value("${content.media.reference-reconciliation.enabled:false}") boolean enabled,
            @Value("${content.media.reference-reconciliation.batch-size:50}") int batchSize
    ) {
        this.applicationService = applicationService;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(500, batchSize));
    }

    @Scheduled(fixedDelayString = "${content.media.reference-reconciliation.delay-ms:300000}")
    public void reconcile() {
        if (!enabled) {
            return;
        }
        try {
            PostMediaReferenceReconciliationResult result = applicationService.reconcile(
                    new ReconcilePostMediaReferencesCommand(cursor, batchSize)
            );
            cursor = result.hasMore() ? result.nextAfterAssetId() : ZERO_UUID;
        } catch (RuntimeException exception) {
            log.warn("[content-media-reference] reconciliation failed: {}", exception.toString());
        }
    }
}
