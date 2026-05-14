package com.nowcoder.community.common.observability.logging;

public final class RuntimeLogFields {

    public static final String COMMUNITY_CATEGORY = "community.category";
    public static final String COMMUNITY_ACTION = "community.action";
    public static final String COMMUNITY_OUTCOME = "community.outcome";

    public static final String EVENT_CATEGORY = "event.category";
    public static final String EVENT_ACTION = "event.action";
    public static final String EVENT_OUTCOME = "event.outcome";
    public static final String EVENT_TRUNCATED = "event.truncated";

    public static final String ERROR_TYPE = "error.type";
    public static final String ERROR_MESSAGE = "error.message";

    public static final String DURATION_MS = "duration.ms";
    public static final String THRESHOLD_MS = "threshold.ms";
    public static final String THRESHOLD_PERCENT = "threshold.percent";

    private RuntimeLogFields() {
    }
}
