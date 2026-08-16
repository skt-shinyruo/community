package com.nowcoder.community.common.outbox;

import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.common.logging.EventLogMessage;
import com.nowcoder.community.common.trace.TraceJobRunner;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring scheduler wrapper for {@link OutboxWorker}.
 */
public class OutboxWorkerScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorkerScheduler.class);
    private static final String CATEGORY_ASYNC = "async";
    private static final String MDC_CATEGORY = EventLogFields.EVENT_CATEGORY;
    private static final String MDC_ACTION = EventLogFields.EVENT_ACTION;
    private static final String MDC_OUTCOME = EventLogFields.EVENT_OUTCOME;

    private final OutboxWorker worker;
    private final OutboxProperties properties;

    public OutboxWorkerScheduler(
            JdbcOutboxEventStore store,
            ObjectProvider<List<OutboxHandler>> handlersProvider,
            OutboxProperties properties,
            Clock clock
    ) {
        this(store, handlersProvider, properties, clock, null);
    }

    public OutboxWorkerScheduler(
            JdbcOutboxEventStore store,
            ObjectProvider<List<OutboxHandler>> handlersProvider,
            OutboxProperties properties,
            Clock clock,
            ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        List<OutboxHandler> handlers = handlersProvider == null ? null : handlersProvider.getIfAvailable();
        MeterRegistry meterRegistry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
        Map<String, OutboxHandler> handlerMap = new HashMap<>();
        if (handlers != null) {
            for (OutboxHandler handler : handlers) {
                if (handler == null) {
                    continue;
                }
                String declaredTopic = handler.topic();
                if (declaredTopic == null || declaredTopic.isBlank()) {
                    continue;
                }
                String topic = declaredTopic.trim();
                OutboxHandler existing = handlerMap.putIfAbsent(topic, handler);
                if (existing != null) {
                    throw new IllegalStateException(
                            "Duplicate outbox handlers for topic '" + topic + "': " +
                                    existing.getClass().getName() + " and " + handler.getClass().getName()
                    );
                }
            }
        }

        this.properties = properties == null ? new OutboxProperties() : properties;
        this.worker = new OutboxWorker(store, Map.copyOf(handlerMap), this.properties, clock, meterRegistry);
    }

    @Scheduled(fixedDelayString = "${events.outbox.worker-fixed-delay-ms:1000}")
    public void poll() {
        TraceJobRunner.run("outbox-worker", () -> {
            try {
                int processed = worker.pollOnce();
                if (processed > 0) {
                    infoEvent(
                            "outbox_poll",
                            "success",
                            "community.batch_size", properties.getBatchSize(),
                            "community.processed_count", processed
                    );
                }
            } catch (RuntimeException e) {
                warnEvent(
                        "outbox_poll",
                        "failure",
                        null,
                        "community.reason_code", "poll_failed",
                        "community.error_class", e.getClass().getName(),
                        "community.error_message", e.getMessage()
                );
            }
        });
    }

    @Override
    public void close() {
        worker.close();
    }

    private void infoEvent(String action, String outcome, Object... keyValues) {
        logEvent(action, outcome, false, null, keyValues);
    }

    private void warnEvent(String action, String outcome, Throwable throwable, Object... keyValues) {
        logEvent(action, outcome, true, throwable, keyValues);
    }

    private void logEvent(String action, String outcome, boolean warn, Throwable throwable, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Outbox scheduler event keyValues must contain key/value pairs");
        }
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        MDC.put(MDC_CATEGORY, CATEGORY_ASYNC);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, outcome);
        try {
            String message = EventLogMessage.format(keyValues);
            if (warn) {
                if (throwable == null) {
                    log.warn(message);
                } else {
                    log.warn(message, throwable);
                }
                return;
            }
            log.info(message);
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
        }
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
