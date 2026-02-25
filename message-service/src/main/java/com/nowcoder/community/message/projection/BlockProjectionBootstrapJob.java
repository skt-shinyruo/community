package com.nowcoder.community.message.projection;

import com.nowcoder.community.platform.scheduler.SingleFlightTaskGuard;
import com.nowcoder.community.message.service.SocialBlockScanClient;
import com.nowcoder.community.social.api.rpc.dto.SocialBlockScanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * message-service 拉黑关系投影自举（bootstrap）：
 * - 周期性从 social-service 扫描“当前拉黑关系集合”，回填到本地 user_block_projection
 * - 事件流（BlockRelationChanged）负责实时纠偏；scan 负责冷启动与补洞
 */
@Component
@ConditionalOnProperty(name = "message.projection.block-scan.enabled", havingValue = "true", matchIfMissing = true)
public class BlockProjectionBootstrapJob {

    private static final Logger log = LoggerFactory.getLogger(BlockProjectionBootstrapJob.class);

    private final SocialBlockScanClient socialBlockScanClient;
    private final UserModerationProjectionRepository projectionRepository;
    private final boolean singleFlightEnabled;
    private final int lockTtlSeconds;
    private final ObjectProvider<SingleFlightTaskGuard> singleFlightTaskGuardProvider;
    private final int batchSize;
    private final long rescanIntervalMs;

    private final AtomicInteger afterBlockerUserId = new AtomicInteger(0);
    private final AtomicInteger afterBlockedUserId = new AtomicInteger(0);
    private final AtomicLong nextRescanAfterMs = new AtomicLong(0);

    public BlockProjectionBootstrapJob(
            SocialBlockScanClient socialBlockScanClient,
            UserModerationProjectionRepository projectionRepository,
            @Value("${message.projection.block-scan.single-flight:true}") boolean singleFlightEnabled,
            @Value("${message.projection.block-scan.lock-ttl-seconds:300}") int lockTtlSeconds,
            @Value("${message.projection.block-scan.batch-size:2000}") int batchSize,
            @Value("${message.projection.block-scan.rescan-interval-ms:3600000}") long rescanIntervalMs,
            ObjectProvider<SingleFlightTaskGuard> singleFlightTaskGuardProvider
    ) {
        this.socialBlockScanClient = socialBlockScanClient;
        this.projectionRepository = projectionRepository;
        this.singleFlightEnabled = singleFlightEnabled;
        this.lockTtlSeconds = Math.max(30, lockTtlSeconds);
        this.batchSize = Math.max(1, Math.min(2000, batchSize));
        this.rescanIntervalMs = Math.max(10_000L, rescanIntervalMs);
        this.singleFlightTaskGuardProvider = singleFlightTaskGuardProvider;
    }

    @Scheduled(fixedDelayString = "${message.projection.block-scan.interval-ms:2000}")
    public void bootstrapBlockedRelations() {
        long nowMs = System.currentTimeMillis();
        if (afterBlockerUserId.get() == 0 && afterBlockedUserId.get() == 0) {
            long allowAt = Math.max(0L, nextRescanAfterMs.get());
            if (nowMs < allowAt) {
                return;
            }
        }

        SingleFlightTaskGuard guard = singleFlightTaskGuardProvider == null ? null : singleFlightTaskGuardProvider.getIfAvailable();
        SingleFlightTaskGuard.Lock lock = null;
        if (singleFlightEnabled && guard != null) {
            lock = guard.tryAcquire("message:projection_bootstrap_blocks", Duration.ofSeconds(lockTtlSeconds));
            if (lock == null) {
                return;
            }
        }

        int afterA = Math.max(0, afterBlockerUserId.get());
        int afterB = Math.max(0, afterBlockedUserId.get());
        try {
            SocialBlockScanResponse resp = socialBlockScanClient.scan(afterA, afterB, batchSize);
            if (resp == null || resp.getItems().isEmpty()) {
                if (resp == null || !resp.isHasMore()) {
                    finishFullScan(nowMs);
                }
                return;
            }

            Instant updatedAt = Instant.now();
            for (SocialBlockScanResponse.SocialBlockScanItem i : resp.getItems()) {
                if (i == null) {
                    continue;
                }
                int blocker = i.getBlockerUserId();
                int blocked = i.getBlockedUserId();
                if (blocker > 0 && blocked > 0 && blocker != blocked) {
                    projectionRepository.upsertBlockRelation(blocker, blocked, true, updatedAt);
                }
            }

            Integer nextA = resp.getNextAfterBlockerUserId();
            Integer nextB = resp.getNextAfterBlockedUserId();
            if (resp.isHasMore() && nextA != null && nextB != null) {
                afterBlockerUserId.set(Math.max(0, nextA));
                afterBlockedUserId.set(Math.max(0, nextB));
                return;
            }
            finishFullScan(nowMs);
        } catch (RuntimeException e) {
            log.warn("[projection] bootstrap blocks failed (afterA={}, afterB={}): {}", afterA, afterB, e.toString());
        } finally {
            if (lock != null && guard != null) {
                guard.release(lock);
            }
        }
    }

    private void finishFullScan(long nowMs) {
        afterBlockerUserId.set(0);
        afterBlockedUserId.set(0);
        nextRescanAfterMs.set(nowMs + rescanIntervalMs);
    }
}

