package com.nowcoder.community.common.observability.app;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogSanitizer;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

import java.time.Duration;
import java.util.List;

public class ApplicationLifecycleRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;
    private final String serviceName;
    private final String serviceVersion;
    private final List<String> profiles;
    private final int serverPort;

    public ApplicationLifecycleRuntimeLogger(
            RuntimeLogWriter logWriter,
            RuntimeLoggingProperties properties,
            String serviceName,
            String serviceVersion,
            List<String> profiles,
            int serverPort
    ) {
        this.logWriter = logWriter;
        this.properties = properties;
        this.serviceName = RuntimeLogSanitizer.text(serviceName);
        this.serviceVersion = RuntimeLogSanitizer.text(serviceVersion);
        this.profiles = profiles == null ? List.of() : List.copyOf(profiles);
        this.serverPort = serverPort;
    }

    public void logStartup(Duration duration) {
        log("app_startup", "success", "application startup", duration);
    }

    public void logReady(Duration duration) {
        log("app_ready", "success", "application ready", duration);
    }

    public void logShutdown(Duration duration) {
        log("app_shutdown", "success", "application shutdown", duration);
    }

    public void logGracefulShutdownTimeout(Duration timeout) {
        log("graceful_shutdown_timeout", "threshold", "application graceful shutdown timeout", timeout);
    }

    private void log(String action, String outcome, String message, Duration duration) {
        if (!properties.isEnabled()) {
            return;
        }
        logWriter.info(RuntimeLogEvent.builder("runtime", action, outcome, message)
                .field("service.name", serviceName)
                .field("service.version", serviceVersion)
                .field("spring.profiles.active", profiles.isEmpty() ? "default" : String.join(",", profiles))
                .field("server.port", serverPort)
                .field("duration.ms", duration == null ? 0 : duration.toMillis())
                .field("jvm.version", System.getProperty("java.version"))
                .field(RuntimeLogFields.THRESHOLD_MS, "threshold".equals(outcome) && duration != null ? duration.toMillis() : null)
                .build());
    }
}
