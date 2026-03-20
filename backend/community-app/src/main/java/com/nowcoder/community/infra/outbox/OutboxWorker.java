package com.nowcoder.community.infra.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Pull-based outbox worker: claims due events and dispatches to topic handlers.
 *
 * <p>Designed for at-least-once delivery (handlers must be idempotent).</p>
 */
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final JdbcOutboxEventStore store;
    private final Map<String, OutboxHandler> handlers;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxWorker(
            JdbcOutboxEventStore store,
            Map<String, OutboxHandler> handlers,
            OutboxProperties properties,
            Clock clock
    ) {
        if (store == null) {
            throw new IllegalArgumentException("store is required");
        }
        this.store = store;
        this.handlers = handlers == null ? Map.of() : handlers;
        this.properties = properties == null ? new OutboxProperties() : properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public int pollOnce() {
        if (!properties.isEnabled()) {
            return 0;
        }

        Instant now = clock.instant();

        // best-effort recovery for stuck leases
        try {
            int recovered = store.recoverExpiredLeases(now);
            if (recovered > 0) {
                log.warn("[outbox] recovered {} expired leases", recovered);
            }
        } catch (RuntimeException e) {
            log.warn("[outbox] recoverExpiredLeases failed: {}", e.toString());
        }

        List<OutboxEvent> due = store.findDuePending(properties.getBatchSize(), now);
        int processed = 0;

        for (OutboxEvent event : due) {
            if (event == null || event.id() <= 0) {
                continue;
            }

            Instant leaseUntil = now.plus(properties.getProcessingLease());
            boolean claimed = store.tryClaimProcessing(event.id(), leaseUntil, now);
            if (!claimed) {
                continue;
            }

            processed++;

            OutboxHandler handler = handlers.get(event.topic());
            if (handler == null) {
                store.markFailedAndScheduleRetry(event.id(), now, now.plus(Duration.ofSeconds(10)), "no handler for topic=" + event.topic());
                continue;
            }

            try {
                handler.handle(event);
                store.markSucceeded(event.id(), now);
            } catch (RuntimeException e) {
                handleFailure(event, now, e);
            }
        }

        return processed;
    }

    private void handleFailure(OutboxEvent event, Instant now, RuntimeException e) {
        int currentRetryCount = Math.max(0, event.retryCount());
        int nextAttemptNumber = currentRetryCount + 1;
        if (nextAttemptNumber > properties.getMaxRetries()) {
            store.markDead(event.id(), now, e.toString());
            log.warn("[outbox] event moved to DEAD (eventId={}, topic={}, retries={}): {}", event.eventId(), event.topic(), nextAttemptNumber, e.toString());
            return;
        }

        Duration delay = backoffDelay(currentRetryCount, properties.getBaseBackoff(), properties.getMaxBackoff());
        Instant nextRetryAt = now.plus(delay);
        store.markFailedAndScheduleRetry(event.id(), now, nextRetryAt, e.toString());
        log.warn(
                "[outbox] handler failed, scheduled retry (eventId={}, topic={}, retryCount={}, nextRetryAt={}): {}",
                event.eventId(),
                event.topic(),
                nextAttemptNumber,
                nextRetryAt,
                e.toString()
        );
    }

    static Duration backoffDelay(int currentRetryCount, Duration base, Duration max) {
        Duration safeBase = base == null ? Duration.ofSeconds(5) : base;
        Duration safeMax = max == null ? Duration.ofMinutes(10) : max;

        int exp = Math.max(0, Math.min(20, currentRetryCount));
        long multiplier = 1L << exp;
        Duration candidate;
        try {
            candidate = safeBase.multipliedBy(multiplier);
        } catch (ArithmeticException ex) {
            candidate = safeMax;
        }
        if (candidate.compareTo(safeMax) > 0) {
            return safeMax;
        }
        return candidate;
    }
}

