package com.nowcoder.community.social.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.social.application.LikeCleanupReconciliationApplicationService;
import com.nowcoder.community.social.application.command.ReconcileLikeCleanupCommand;
import com.nowcoder.community.social.application.result.LikeCleanupReconciliationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;

@Component
public class SocialLikeCleanupReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(SocialLikeCleanupReconciliationJob.class);
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final LikeCleanupReconciliationApplicationService applicationService;
    private final boolean enabled;
    private final int batchSize;
    private UUID postCursor = ZERO_UUID;
    private UUID commentCursor = ZERO_UUID;

    public SocialLikeCleanupReconciliationJob(
            LikeCleanupReconciliationApplicationService applicationService,
            @Value("${social.like-cleanup.reconciliation.enabled:false}") boolean enabled,
            @Value("${social.like-cleanup.reconciliation.batch-size:50}") int batchSize
    ) {
        this.applicationService = applicationService;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(200, batchSize));
    }

    @Scheduled(fixedDelayString = "${social.like-cleanup.reconciliation.delay-ms:300000}")
    public void reconcile() {
        TraceJobRunner.run("social-like-cleanup-reconciliation", () -> {
            if (!enabled) {
                return;
            }
            postCursor = reconcile(POST, postCursor);
            commentCursor = reconcile(COMMENT, commentCursor);
        });
    }

    private UUID reconcile(int entityType, UUID cursor) {
        try {
            LikeCleanupReconciliationResult result = applicationService.reconcile(
                    new ReconcileLikeCleanupCommand(entityType, cursor, batchSize)
            );
            return result.hasMore() ? result.nextAfterEntityId() : ZERO_UUID;
        } catch (RuntimeException exception) {
            log.warn("[social-like-cleanup] reconciliation failed entityType={}: {}", entityType, exception.toString());
            return cursor;
        }
    }
}
