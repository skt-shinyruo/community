package com.nowcoder.community.common.observability.security;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogSanitizer;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

public class SecurityRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;

    public SecurityRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public void logRateLimitTriggered(String control, String subjectType, String path, long current, long threshold) {
        if (!properties.getSecurity().isEnabled()) {
            return;
        }
        logWriter.warn(RuntimeLogEvent.builder("security", "rate_limit_triggered", "threshold", "rate limit triggered")
                .field("security.control", RuntimeLogSanitizer.operation(control))
                .field("security.subject.type", RuntimeLogSanitizer.operation(subjectType))
                .field("url.path", RuntimeLogSanitizer.pathOnly(path))
                .field("rate.limit.current", current)
                .field("rate.limit.threshold", threshold)
                .build());
    }

    public void logAuthFilterError(String control, String path, Throwable throwable) {
        if (!properties.getSecurity().isEnabled()) {
            return;
        }
        RuntimeLogEvent.Builder builder = RuntimeLogEvent.builder("security", "auth_filter_error", "failure", "auth filter error")
                .field("security.control", RuntimeLogSanitizer.operation(control))
                .field("url.path", RuntimeLogSanitizer.pathOnly(path));
        if (throwable != null) {
            builder.field(RuntimeLogFields.ERROR_TYPE, throwable.getClass().getName());
        }
        logWriter.warn(builder.build());
    }
}
