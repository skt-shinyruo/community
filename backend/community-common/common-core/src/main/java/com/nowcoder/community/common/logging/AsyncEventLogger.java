package com.nowcoder.community.common.logging;

import org.slf4j.Logger;

/**
 * Structured event logging for asynchronous (Kafka) processing: binds the
 * event.category/action/outcome MDC fields for the duration of the call and emits the
 * key/value pairs as escaped {@code key=value} tokens via {@link EventLogMessage}.
 */
public final class AsyncEventLogger {

    private static final String CATEGORY = "async";

    private AsyncEventLogger() {
    }

    public static void debug(Logger logger, String action, String outcome, Object... keyValues) {
        try (EventLogMdcScope ignored = EventLogMdcScope.open(CATEGORY, action, outcome)) {
            logger.debug(EventLogMessage.format(keyValues));
        }
    }

    public static void warn(Logger logger, String action, String outcome, Object... keyValues) {
        try (EventLogMdcScope ignored = EventLogMdcScope.open(CATEGORY, action, outcome)) {
            logger.warn(EventLogMessage.format(keyValues));
        }
    }

    public static String exceptionReasonCode(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String simpleName = throwable.getClass().getSimpleName();
        if (simpleName.endsWith("Exception")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Exception".length());
        } else if (simpleName.endsWith("Error")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Error".length());
        }
        if (simpleName.isEmpty()) {
            return "unknown";
        }
        StringBuilder out = new StringBuilder(simpleName.length() + 8);
        for (int i = 0; i < simpleName.length(); i++) {
            char ch = simpleName.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                out.append('_');
            }
            out.append(Character.toLowerCase(ch));
        }
        return out.toString();
    }

    public static String errorClass(Throwable throwable) {
        return throwable == null ? null : throwable.getClass().getName();
    }

    public static String errorMessage(Throwable throwable) {
        return throwable == null ? null : throwable.getMessage();
    }
}
