package com.nowcoder.community.common.observability.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class RuntimeLogWriter {

    private static final int MAX_FIELD_VALUE_LENGTH = 512;

    private final Logger logger;

    public RuntimeLogWriter(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    public void info(RuntimeLogEvent event) {
        withMdc(event, () -> logger.info(formatMessage(event)));
    }

    public void warn(RuntimeLogEvent event) {
        withMdc(event, () -> logger.warn(formatMessage(event)));
    }

    private void withMdc(RuntimeLogEvent event, Runnable callback) {
        Map<String, String> fields = fieldsFor(event);
        Map<String, String> previousValues = new LinkedHashMap<>();
        fields.forEach((key, value) -> previousValues.put(key, MDC.get(key)));
        try {
            fields.forEach(MDC::put);
            callback.run();
        } finally {
            previousValues.forEach(RuntimeLogWriter::restore);
        }
    }

    private Map<String, String> fieldsFor(RuntimeLogEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(RuntimeLogFields.COMMUNITY_CATEGORY, event.category());
        fields.put(RuntimeLogFields.COMMUNITY_ACTION, event.action());
        fields.put(RuntimeLogFields.COMMUNITY_OUTCOME, event.outcome());
        fields.put(RuntimeLogFields.EVENT_CATEGORY, event.category());
        fields.put(RuntimeLogFields.EVENT_ACTION, event.action());
        fields.put(RuntimeLogFields.EVENT_OUTCOME, event.outcome());
        event.fields().forEach((key, value) -> fields.put(key, safeValue(value)));
        return fields;
    }

    private String formatMessage(RuntimeLogEvent event) {
        StringBuilder message = new StringBuilder(event.message());
        event.fields().forEach((key, value) -> {
            if (isMessageField(key) && value != null) {
                message.append(' ').append(key).append('=').append(safeValue(value));
            }
        });
        return message.toString();
    }

    private boolean isMessageField(String key) {
        return !RuntimeLogFields.COMMUNITY_CATEGORY.equals(key)
                && !RuntimeLogFields.COMMUNITY_ACTION.equals(key)
                && !RuntimeLogFields.COMMUNITY_OUTCOME.equals(key)
                && !RuntimeLogFields.EVENT_CATEGORY.equals(key)
                && !RuntimeLogFields.EVENT_ACTION.equals(key)
                && !RuntimeLogFields.EVENT_OUTCOME.equals(key);
    }

    private static String safeValue(String value) {
        if (value.length() <= MAX_FIELD_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_FIELD_VALUE_LENGTH) + "...";
    }

    private static void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
