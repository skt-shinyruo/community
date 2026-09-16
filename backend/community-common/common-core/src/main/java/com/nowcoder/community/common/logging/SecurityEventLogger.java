package com.nowcoder.community.common.logging;

import org.slf4j.Logger;

public final class SecurityEventLogger {

    private static final String CATEGORY = "security";

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

        try (var ignored = EventLogMdcScope.open(CATEGORY, action, outcome)) {
            String message = EventLogMessage.format(keyValues);
            if (warn) {
                logger.warn(message);
                return;
            }
            logger.info(message);
        }
    }
}
