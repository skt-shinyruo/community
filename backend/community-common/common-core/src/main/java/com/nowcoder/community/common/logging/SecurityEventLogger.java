package com.nowcoder.community.common.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;

public final class SecurityEventLogger {

    private static final String CATEGORY = "security";
    private static final String MDC_CATEGORY = EventLogFields.EVENT_CATEGORY;
    private static final String MDC_ACTION = EventLogFields.EVENT_ACTION;
    private static final String MDC_OUTCOME = EventLogFields.EVENT_OUTCOME;

    private SecurityEventLogger() {
    }

    public static void info(Logger logger, String action, String outcome, Object... keyValues) {
        log(logger, false, action, outcome, keyValues);
    }

    public static void warn(Logger logger, String action, String outcome, Object... keyValues) {
        log(logger, true, action, outcome, keyValues);
    }

    private static void log(Logger logger, boolean warn, String action, String outcome, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Security event keyValues must contain key/value pairs");
        }

        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        MDC.put(MDC_CATEGORY, CATEGORY);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, outcome);
        try {
            String message = EventLogMessage.format(keyValues);
            if (warn) {
                logger.warn(message);
                return;
            }
            logger.info(message);
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
        }
    }

    private static void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
