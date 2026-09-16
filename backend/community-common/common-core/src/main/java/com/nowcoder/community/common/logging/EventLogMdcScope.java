package com.nowcoder.community.common.logging;

import org.slf4j.MDC;

/**
 * Scoped binding of the structured event MDC fields: captures the previous
 * {@code event.category}/{@code event.action}/{@code event.outcome} values on
 * {@link #open} and restores them on {@link #close} (previously absent keys are removed).
 */
public final class EventLogMdcScope implements AutoCloseable {

    private final String previousCategory;
    private final String previousAction;
    private final String previousOutcome;

    private EventLogMdcScope(String previousCategory, String previousAction, String previousOutcome) {
        this.previousCategory = previousCategory;
        this.previousAction = previousAction;
        this.previousOutcome = previousOutcome;
    }

    public static EventLogMdcScope open(String category, String action, String outcome) {
        String previousCategory = MDC.get(EventLogFields.EVENT_CATEGORY);
        String previousAction = MDC.get(EventLogFields.EVENT_ACTION);
        String previousOutcome = MDC.get(EventLogFields.EVENT_OUTCOME);
        MDC.put(EventLogFields.EVENT_CATEGORY, category);
        MDC.put(EventLogFields.EVENT_ACTION, action);
        MDC.put(EventLogFields.EVENT_OUTCOME, outcome);
        return new EventLogMdcScope(previousCategory, previousAction, previousOutcome);
    }

    @Override
    public void close() {
        restore(EventLogFields.EVENT_CATEGORY, previousCategory);
        restore(EventLogFields.EVENT_ACTION, previousAction);
        restore(EventLogFields.EVENT_OUTCOME, previousOutcome);
    }

    private static void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
