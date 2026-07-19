package com.nowcoder.community.common.outbox;

import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.common.trace.OtelTraceContext;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.SpanKind;
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
    private static final String MDC_CATEGORY = EventLogFields.EVENT_CATEGORY;
    private static final String MDC_ACTION = EventLogFields.EVENT_ACTION;
    private static final String MDC_OUTCOME = EventLogFields.EVENT_OUTCOME;
    private static final String LEASE_LOST_METRIC = "outbox.lease.lost";
    private static final String UNHANDLED_TOPIC = "unhandled";
    private static final String TRANSITION_SUCCESS = "success";
    private static final String TRANSITION_RETRY = "retry";
    private static final String TRANSITION_DEAD = "dead";

    private final JdbcOutboxEventStore store;
    private final Map<String, OutboxHandler> handlers;
    private final OutboxProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public OutboxWorker(
            JdbcOutboxEventStore store,
            Map<String, OutboxHandler> handlers,
            OutboxProperties properties,
            Clock clock
    ) {
        this(store, handlers, properties, clock, null);
    }

    public OutboxWorker(
            JdbcOutboxEventStore store,
            Map<String, OutboxHandler> handlers,
            OutboxProperties properties,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        if (store == null) {
            throw new IllegalArgumentException("store is required");
        }
        this.store = store;
        this.handlers = handlers == null ? Map.of() : handlers;
        this.properties = properties == null ? new OutboxProperties() : properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.meterRegistry = meterRegistry;
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
            OutboxLease lease = store.tryClaimProcessing(event.id(), leaseUntil, now).orElse(null);
            if (lease == null) {
                continue;
            }

            processed++;

            TraceContextSnapshot snapshot = TraceContextSnapshot.fromStored(event.traceId(), event.traceparent());
            try (var ignored = OtelTraceContext.openForInbound(
                    snapshot.traceparent(),
                    "outbox.process " + event.topic(),
                    SpanKind.CONSUMER
            )) {
                OutboxHandler handler = handlers.get(event.topic());
                if (handler == null) {
                    Instant nextRetryAt = now.plus(Duration.ofSeconds(10));
                    boolean retryScheduled = store.markFailedAndScheduleRetry(
                            lease,
                            now,
                            nextRetryAt,
                            "no handler for topic=" + event.topic()
                    );
                    if (!retryScheduled) {
                        recordLeaseLost(event, TRANSITION_RETRY);
                        continue;
                    }
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
                } catch (RuntimeException e) {
                    handleFailure(event, lease, now, e);
                    continue;
                }

                if (!store.markSucceeded(lease, now)) {
                    recordLeaseLost(event, TRANSITION_SUCCESS);
                    continue;
                }
            }
        }

        return processed;
    }

    private void handleFailure(OutboxEvent event, OutboxLease lease, Instant now, RuntimeException error) {
        int currentRetryCount = Math.max(0, event.retryCount());
        int nextAttemptNumber = currentRetryCount + 1;
        if (nextAttemptNumber > properties.getMaxRetries()) {
            if (!store.markDead(lease, now, error.toString())) {
                recordLeaseLost(event, TRANSITION_DEAD, error.getClass().getName());
                return;
            }
            warnEvent(
                    "outbox_dispatch",
                    "dead",
                    null,
                    "community.event_id", event.eventId(),
                    "community.topic", event.topic(),
                    "community.retry_count", nextAttemptNumber,
                    "community.error_class", error.getClass().getName(),
                    "community.error_message", error.getMessage()
            );
            return;
        }

        Duration delay = backoffDelay(currentRetryCount, properties.getBaseBackoff(), properties.getMaxBackoff());
        Instant nextRetryAt = now.plus(delay);
        if (!store.markFailedAndScheduleRetry(lease, now, nextRetryAt, error.toString())) {
            recordLeaseLost(event, TRANSITION_RETRY, error.getClass().getName());
            return;
        }
        warnEvent(
                "outbox_dispatch",
                "retry",
                null,
                "community.event_id", event.eventId(),
                "community.topic", event.topic(),
                "community.retry_count", nextAttemptNumber,
                "community.next_retry_at", nextRetryAt,
                "community.error_class", error.getClass().getName(),
                "community.error_message", error.getMessage()
        );
    }

    private void recordLeaseLost(OutboxEvent event, String transition) {
        recordLeaseLost(event, transition, null);
    }

    private void recordLeaseLost(OutboxEvent event, String transition, String errorClass) {
        int retryCount = leaseLostRetryCount(event, transition);
        if (errorClass == null) {
            warnEvent(
                    "outbox_lease_lost",
                    "degraded",
                    null,
                    "community.event_id", event.eventId(),
                    "community.topic", event.topic(),
                    "community.transition", transition,
                    "community.retry_count", retryCount
            );
        } else {
            warnEvent(
                    "outbox_lease_lost",
                    "degraded",
                    null,
                    "community.event_id", event.eventId(),
                    "community.topic", event.topic(),
                    "community.transition", transition,
                    "community.retry_count", retryCount,
                    "community.error_class", errorClass
            );
        }

        if (meterRegistry != null) {
            meterRegistry.counter(
                    LEASE_LOST_METRIC,
                    "topic", metricTopic(event.topic()),
                    "transition", transition
            ).increment();
        }
    }

    private int leaseLostRetryCount(OutboxEvent event, String transition) {
        int currentRetryCount = Math.max(0, event.retryCount());
        if (TRANSITION_SUCCESS.equals(transition) || currentRetryCount == Integer.MAX_VALUE) {
            return currentRetryCount;
        }
        return currentRetryCount + 1;
    }

    private String metricTopic(String eventTopic) {
        return handlers.containsKey(eventTopic) ? eventTopic : UNHANDLED_TOPIC;
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
