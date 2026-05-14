package com.nowcoder.community.common.observability.redis;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogSanitizer;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

public class RedisRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;

    public RedisRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public boolean logConnectionPressure(ConnectionSnapshot snapshot) {
        int threshold = properties.getRedis().getPoolPendingThreshold();
        if (snapshot == null || snapshot.pending() < threshold) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("cache", "redis_connection_pressure", "threshold", "redis connection pressure")
                .field("cache.system", "redis")
                .field("cache.mode", RuntimeLogSanitizer.operation(snapshot.mode()))
                .field("net.peer.name", RuntimeLogSanitizer.text(snapshot.peerName()))
                .field("cache.pool.active", snapshot.active())
                .field("cache.pool.idle", snapshot.idle())
                .field("cache.pool.pending", snapshot.pending())
                .field("threshold.count", threshold)
                .build());
        return true;
    }

    public boolean logSlowCommand(String operation, String redisKey, long durationMs, Throwable throwable) {
        long threshold = properties.getRedis().getSlowCommandThresholdMs();
        if (durationMs < threshold) {
            return false;
        }
        RuntimeLogEvent.Builder builder = RuntimeLogEvent.builder("cache", "redis_command_slow", throwable == null ? "threshold" : "failure", "redis command slow")
                .field("cache.system", "redis")
                .field("cache.operation", RuntimeLogSanitizer.uppercaseOperation(operation))
                .field(RuntimeLogFields.DURATION_MS, durationMs)
                .field(RuntimeLogFields.THRESHOLD_MS, threshold);
        if (throwable != null) {
            builder.field(RuntimeLogFields.ERROR_TYPE, throwable.getClass().getName());
        }
        logWriter.warn(builder.build());
        return true;
    }

    public record ConnectionSnapshot(String mode, String peerName, int active, int idle, int pending) {
    }
}
