package com.nowcoder.community.common.observability.job;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogSanitizer;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

public class ScheduledJobRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;

    public ScheduledJobRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public boolean logSlowJob(String jobName, long durationMs) {
        long threshold = properties.getJobs().getSlowJobThresholdMs();
        if (durationMs < threshold) {
            return false;
        }
        logWriter.warn(base("scheduled_job_slow", "threshold", "scheduled job slow", jobName)
                .field(RuntimeLogFields.DURATION_MS, durationMs)
                .field(RuntimeLogFields.THRESHOLD_MS, threshold)
                .build());
        return true;
    }

    public void logSkipped(String jobName, String reason) {
        logWriter.info(base("scheduled_job_skipped", "skipped", "scheduled job skipped", jobName)
                .field("job.skip.reason", RuntimeLogSanitizer.operation(reason))
                .build());
    }

    public void logError(String jobName, Throwable throwable) {
        RuntimeLogEvent.Builder builder = base("scheduled_job_error", "failure", "scheduled job error", jobName);
        if (throwable != null) {
            builder.field(RuntimeLogFields.ERROR_TYPE, throwable.getClass().getName());
        }
        logWriter.warn(builder.build());
    }

    private RuntimeLogEvent.Builder base(String action, String outcome, String message, String jobName) {
        return RuntimeLogEvent.builder("job", action, outcome, message)
                .field("job.system", "spring-scheduled")
                .field("job.name", RuntimeLogSanitizer.operation(jobName));
    }
}
