package com.nowcoder.community.infra.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Spring scheduler wrapper for {@link OutboxWorker}.
 */
public class OutboxWorkerScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorkerScheduler.class);
    private static final String CATEGORY_ASYNC = "async";
    private static final String MDC_CATEGORY = "community.category";
    private static final String MDC_ACTION = "community.action";
    private static final String MDC_OUTCOME = "community.outcome";

    private final OutboxWorker worker;
    private final OutboxProperties properties;

    public OutboxWorkerScheduler(
            JdbcOutboxEventStore store,
            ObjectProvider<List<OutboxHandler>> handlersProvider,
            OutboxProperties properties,
            Clock clock
    ) {
        List<OutboxHandler> handlers = handlersProvider == null ? null : handlersProvider.getIfAvailable();
        Map<String, OutboxHandler> handlerMap = new HashMap<>();
        if (handlers != null) {
            for (OutboxHandler handler : handlers) {
                if (handler == null || handler.topic() == null || handler.topic().isBlank()) {
                    continue;
                }
                handlerMap.put(handler.topic(), handler);
            }
        }

        this.properties = properties == null ? new OutboxProperties() : properties;
        this.worker = new OutboxWorker(store, Map.copyOf(handlerMap), this.properties, clock);
    }

    @Scheduled(fixedDelayString = "${events.outbox.worker-fixed-delay-ms:1000}")
    public void poll() {
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
                    e,
                    "community.reason_code", "poll_failed"
            );
        }
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
            String message = buildMessage(action, outcome, keyValues);
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

    private String buildMessage(String action, String outcome, Object... keyValues) {
        StringBuilder message = new StringBuilder(160);
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
