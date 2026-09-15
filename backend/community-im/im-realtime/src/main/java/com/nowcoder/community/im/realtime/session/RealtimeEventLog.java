package com.nowcoder.community.im.realtime.session;

import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.common.logging.EventLogMessage;
import com.nowcoder.community.common.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * Shared structured event logging for realtime connection/frame events. Centralizes
 * the MDC category/action/outcome/trace-id dance so the transport handler, frame
 * module and lifecycle service emit the same field contract.
 */
public final class RealtimeEventLog {

    public static final String CATEGORY_ACCESS = "access";
    public static final String CATEGORY_SECURITY = "security";

    private static final String MDC_CATEGORY = EventLogFields.EVENT_CATEGORY;
    private static final String MDC_ACTION = EventLogFields.EVENT_ACTION;
    private static final String MDC_OUTCOME = EventLogFields.EVENT_OUTCOME;
    private static final String MDC_TRACE_ID = TraceContext.MDC_KEY_TRACE_ID;

    private RealtimeEventLog() {
    }

    public static void info(Logger log, String category, String action, String outcome, String traceId, Object... keyValues) {
        emit(log, category, action, outcome, traceId, false, keyValues);
    }

    public static void warn(Logger log, String category, String action, String outcome, String traceId, Object... keyValues) {
        emit(log, category, action, outcome, traceId, true, keyValues);
    }

    private static void emit(Logger log, String category, String action, String outcome, String traceId, boolean warn, Object... keyValues) {
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        String previousTraceId = MDC.get(MDC_TRACE_ID);
        MDC.put(MDC_CATEGORY, category);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, outcome);
        if (StringUtils.hasText(traceId)) {
            MDC.put(MDC_TRACE_ID, traceId);
        } else {
            MDC.remove(MDC_TRACE_ID);
        }
        try {
            String message = EventLogMessage.format(keyValues);
            if (warn) {
                log.warn(message);
            } else {
                log.info(message);
            }
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
            restore(MDC_TRACE_ID, previousTraceId);
        }
    }

    private static void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }
}
