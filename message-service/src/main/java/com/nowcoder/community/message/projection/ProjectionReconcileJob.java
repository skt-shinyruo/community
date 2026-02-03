package com.nowcoder.community.message.projection;

import com.nowcoder.community.common.scheduler.SingleFlightTaskGuard;
import com.nowcoder.community.message.service.UserModerationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * message-service 本地投影回填/纠偏：周期性从 user-service 扫描处罚状态，回填到本地投影表。
 */
@Component
@ConditionalOnProperty(name = "message.projection.reconcile.enabled", havingValue = "true", matchIfMissing = false)
public class ProjectionReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(ProjectionReconcileJob.class);

    private final UserModerationClient userModerationClient;
    private final UserModerationProjectionRepository projectionRepository;
    private final boolean singleFlightEnabled;
    private final int lockTtlSeconds;
    private final ObjectProvider<SingleFlightTaskGuard> singleFlightTaskGuardProvider;

    private final AtomicInteger moderationAfterId = new AtomicInteger(0);

    public ProjectionReconcileJob(
            UserModerationClient userModerationClient,
            UserModerationProjectionRepository projectionRepository,
            @Value("${message.projection.reconcile.single-flight:true}") boolean singleFlightEnabled,
            @Value("${message.projection.reconcile.lock-ttl-seconds:300}") int lockTtlSeconds,
            ObjectProvider<SingleFlightTaskGuard> singleFlightTaskGuardProvider
    ) {
        this.userModerationClient = userModerationClient;
        this.projectionRepository = projectionRepository;
        this.singleFlightEnabled = singleFlightEnabled;
        this.lockTtlSeconds = Math.max(30, lockTtlSeconds);
        this.singleFlightTaskGuardProvider = singleFlightTaskGuardProvider;
    }

    @Scheduled(fixedDelayString = "${message.projection.reconcile.interval-ms:60000}")
    public void reconcileModerationProjection() {
        SingleFlightTaskGuard guard = singleFlightTaskGuardProvider == null ? null : singleFlightTaskGuardProvider.getIfAvailable();
        SingleFlightTaskGuard.Lock lock = null;
        if (singleFlightEnabled && guard != null) {
            lock = guard.tryAcquire("message:projection_reconcile_moderation", Duration.ofSeconds(lockTtlSeconds));
            if (lock == null) {
                return;
            }
        }

        int afterId = Math.max(0, moderationAfterId.get());
        int batchSize = 200;
        try {
            List<UserModerationClient.ModerationStatus> list = userModerationClient.scanStatuses(afterId, batchSize);
            if (list == null || list.isEmpty()) {
                moderationAfterId.set(0);
                return;
            }
            Instant now = Instant.now();
            int maxId = afterId;
            for (UserModerationClient.ModerationStatus s : list) {
                if (s == null || s.getUserId() <= 0) {
                    continue;
                }
                projectionRepository.upsertModerationStatus(s.getUserId(), s.getMuteUntil(), s.getBanUntil(), now);
                if (s.getUserId() > maxId) {
                    maxId = s.getUserId();
                }
            }
            moderationAfterId.set(maxId);
        } catch (Exception e) {
            log.warn("[projection] reconcile moderation failed (afterId={}): {}", afterId, e.toString());
        } finally {
            if (lock != null && guard != null) {
                guard.release(lock);
            }
        }
    }
}
