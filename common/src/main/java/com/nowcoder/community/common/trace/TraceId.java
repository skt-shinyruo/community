package com.nowcoder.community.common.trace;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceId {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    private TraceId() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String currentOrNull() {
        return MDC.get(MDC_KEY);
    }

    public static void put(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        MDC.put(MDC_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}

