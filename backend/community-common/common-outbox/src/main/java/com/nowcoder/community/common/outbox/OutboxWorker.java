package com.nowcoder.community.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pull-based outbox worker: claims due events and dispatches to topic handlers.
 *
 * <p>Designed for at-least-once delivery (handlers must be idempotent).</p>
 */
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final String CATEGORY_ASYNC = "async";
    private static final String MDC_CATEGORY = "community.category";
    private static final String MDC_ACTION = "community.action";
    private static final String MDC_OUTCOME = "community.outcome";

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
                warnEvent(
                        "outbox_lease_recovery",
                        "degraded",
                        null,
                        "community.reason_code", "expired_leases_recovered",
                        "community.recovered_count", recovered
                );
            }
        } catch (RuntimeException e) {
            warnEvent(
                    "outbox_lease_recovery",
                    "failure",
                    e,
                    "community.reason_code", "recover_failed"
            );
        }

        List<OutboxEvent> due = store.findDuePending(properties.getBatchSize(), now);
        int processed = 0;

        for (OutboxEvent event : due) {
            if (event == null || event.id() == null) {
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
                Instant nextRetryAt = now.plus(Duration.ofSeconds(10));
                store.markFailedAndScheduleRetry(event.id(), now, nextRetryAt, "no handler for topic=" + event.topic());
                warnEvent(
                        "outbox_dispatch",
                        "degraded",
                        null,
                        "community.reason_code", "no_handler",
                        "community.event_id", event.eventId(),
                        "community.topic", event.topic(),
                        "community.retry_count", Math.max(0, event.retryCount()) + 1,
                        "community.next_retry_at", nextRetryAt
                );
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
            warnEvent(
                    "outbox_dispatch",
                    "dead",
                    null,
                    "community.event_id", event.eventId(),
                    "community.topic", event.topic(),
                    "community.retry_count", nextAttemptNumber,
                    "community.error_class", e.getClass().getName(),
                    "community.error_message", e.getMessage()
            );
            return;
        }

        Duration delay = backoffDelay(currentRetryCount, properties.getBaseBackoff(), properties.getMaxBackoff());
        Instant nextRetryAt = now.plus(delay);
        store.markFailedAndScheduleRetry(event.id(), now, nextRetryAt, e.toString());
        warnEvent(
                "outbox_dispatch",
                "retry",
                null,
                "community.event_id", event.eventId(),
                "community.topic", event.topic(),
                "community.retry_count", nextAttemptNumber,
                "community.next_retry_at", nextRetryAt,
                "community.error_class", e.getClass().getName(),
                "community.error_message", e.getMessage()
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

    private void warnEvent(String action, String outcome, Throwable throwable, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Outbox event keyValues must contain key/value pairs");
        }
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        MDC.put(MDC_CATEGORY, CATEGORY_ASYNC);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, outcome);
        try {
            String message = buildMessage(action, outcome, keyValues);
            if (throwable == null) {
                log.warn(message);
            } else {
                log.warn(message, throwable);
            }
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
        }
    }

    private String buildMessage(String action, String outcome, Object... keyValues) {
        StringBuilder message = new StringBuilder(192);
        appendToken(message, MDC_CATEGORY, CATEGORY_ASYNC);
        appendToken(message, MDC_ACTION, action);
        appendToken(message, MDC_OUTCOME, outcome);
        for (int i = 0; i < keyValues.length; i += 2) {
            appendToken(message, String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return message.toString();
    }

    private void appendToken(StringBuilder message, String key, Object value) {
        if (message.length() > 0) {
            message.append(' ');
        }
        message.append(key).append('=').append(encodeTokenValue(value));
    }

    private String encodeTokenValue(Object value) {
        if (value == null) {
            return "-";
        }
        String raw = String.valueOf(value);
        if (raw.isEmpty()) {
            return "-";
        }
        StringBuilder encoded = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '=' || ch == '%') {
                encoded.append('%');
                String hex = Integer.toHexString(ch).toUpperCase(Locale.ROOT);
                if (hex.length() == 1) {
                    encoded.append('0');
                }
                encoded.append(hex);
            } else {
                encoded.append(ch);
            }
        }
        return encoded.toString();
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
