package com.nowcoder.community.common.observability.logging;

public class LoggingSystemRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;

    public LoggingSystemRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public void logAppenderError(String appenderName, Throwable throwable) {
        RuntimeLogEvent.Builder builder = RuntimeLogEvent.builder("logging", "logging_appender_error", "failure", "logging appender error")
                .field("logging.appender.name", RuntimeLogSanitizer.text(appenderName));
        if (throwable != null) {
            builder.field(RuntimeLogFields.ERROR_TYPE, throwable.getClass().getName());
        }
        logWriter.warn(builder.build());
    }

    public boolean logQueuePressure(String queueName, long used, long capacity) {
        long usedPercent = RuntimeLogSanitizer.percent(used, capacity);
        int threshold = properties.getLoggingSystem().getQueuePressureThresholdPercent();
        if (capacity <= 0 || usedPercent < threshold) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("logging", "logging_queue_pressure", "threshold", "logging queue pressure")
                .field("logging.queue.name", RuntimeLogSanitizer.text(queueName))
                .field("logging.queue.used", used)
                .field("logging.queue.capacity", capacity)
                .field("logging.queue.used.percent", usedPercent)
                .field(RuntimeLogFields.THRESHOLD_PERCENT, threshold)
                .build());
        return true;
    }
}
