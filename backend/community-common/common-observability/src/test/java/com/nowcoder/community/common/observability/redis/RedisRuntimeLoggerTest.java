package com.nowcoder.community.common.observability.redis;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRuntimeLoggerTest {

    @Test
    void logsConnectionPressureAndSlowCommandsWithoutRedisKeys() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.redis-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getRedis().setPoolPendingThreshold(2);
            properties.getRedis().setSlowCommandThresholdMs(100);
            RedisRuntimeLogger logger = new RedisRuntimeLogger(capture.writer(), properties);

            assertThat(logger.logConnectionPressure(new RedisRuntimeLogger.ConnectionSnapshot("standalone", "redis.local", 5, 1, 1))).isFalse();
            assertThat(logger.logConnectionPressure(new RedisRuntimeLogger.ConnectionSnapshot("standalone", "redis.local", 5, 1, 2))).isTrue();
            assertThat(logger.logSlowCommand("GET", "auth:token:raw-key", 99, null)).isFalse();
            assertThat(logger.logSlowCommand("GET", "auth:token:raw-key", 101, new IllegalStateException("timeout\nsecret"))).isTrue();

            assertThat(capture.appender().list).hasSize(2);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_CATEGORY, "cache")
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "redis_connection_pressure")
                    .containsEntry("cache.system", "redis")
                    .containsEntry("cache.mode", "standalone")
                    .containsEntry("net.peer.name", "redis.local")
                    .containsEntry("cache.pool.active", "5")
                    .containsEntry("cache.pool.idle", "1")
                    .containsEntry("cache.pool.pending", "2");
            assertThat(capture.appender().list.get(1).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "redis_command_slow")
                    .containsEntry("cache.operation", "GET")
                    .containsEntry("duration.ms", "101")
                    .containsEntry("threshold.ms", "100")
                    .containsEntry("error.type", IllegalStateException.class.getName())
                    .doesNotContainKey("redis.key")
                    .doesNotContainValue("timeout secret");
            assertThat(capture.appender().list.get(1).getFormattedMessage())
                    .doesNotContain("auth:token:raw-key")
                    .doesNotContain("timeout secret");
        }
    }
}
