package com.nowcoder.community.message.kafka;

// message-service 幂等表清理任务：按 retention-days 删除过期记录。
import com.nowcoder.community.common.scheduler.SingleFlightTaskGuard;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "message.idempotency.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class ConsumedEventCleanupJob {

    private final JdbcTemplate jdbcTemplate;
    private final int retentionDays;
    private final int batchSize;
    private final int maxBatches;
    private final boolean singleFlightEnabled;
    private final int lockTtlSeconds;
    private final ObjectProvider<SingleFlightTaskGuard> singleFlightTaskGuardProvider;
    private final MeterRegistry meterRegistry;

    public ConsumedEventCleanupJob(
            JdbcTemplate jdbcTemplate,
            @Value("${message.idempotency.retention-days:7}") int retentionDays,
            @Value("${message.idempotency.cleanup-batch-size:1000}") int batchSize,
            @Value("${message.idempotency.cleanup-max-batches:50}") int maxBatches,
            @Value("${message.idempotency.cleanup-single-flight:true}") boolean singleFlightEnabled,
            @Value("${message.idempotency.cleanup-lock-ttl-seconds:600}") int lockTtlSeconds,
            ObjectProvider<SingleFlightTaskGuard> singleFlightTaskGuardProvider,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionDays = Math.max(1, retentionDays);
        this.batchSize = Math.max(1, Math.min(5000, batchSize));
        this.maxBatches = Math.max(1, Math.min(500, maxBatches));
        this.singleFlightEnabled = singleFlightEnabled;
        this.lockTtlSeconds = Math.max(10, lockTtlSeconds);
        this.singleFlightTaskGuardProvider = singleFlightTaskGuardProvider;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${message.idempotency.cleanup-interval-ms:21600000}")
    public void cleanup() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(retentionDays, ChronoUnit.DAYS));

        SingleFlightTaskGuard guard = singleFlightTaskGuardProvider == null ? null : singleFlightTaskGuardProvider.getIfAvailable();
        SingleFlightTaskGuard.Lock lock = null;
        if (singleFlightEnabled && guard != null) {
            lock = guard.tryAcquire("message:consumed_event_cleanup", Duration.ofSeconds(lockTtlSeconds));
            if (lock == null) {
                meterRegistry.counter("message_idempotency_cleanup_total", Tags.of("outcome", "skip_locked")).increment();
                return;
            }
        }

        int total = 0;
        try {
            for (int i = 0; i < maxBatches; i++) {
                int deleted = jdbcTemplate.update(
                        "delete from consumed_event where consumed_at < ? order by consumed_at, id limit ?",
                        cutoff,
                        batchSize
                );
                if (deleted <= 0) {
                    break;
                }
                total += deleted;
                if (deleted < batchSize) {
                    break;
                }
            }
            meterRegistry.counter("message_idempotency_cleanup_total", Tags.of("outcome", "success")).increment();
            meterRegistry.counter("message_idempotency_cleanup_deleted_total").increment(total);
        } catch (Exception e) {
            meterRegistry.counter("message_idempotency_cleanup_total", Tags.of("outcome", "failed")).increment();
            throw e;
        } finally {
            if (lock != null && guard != null) {
                guard.release(lock);
            }
        }
    }
}
