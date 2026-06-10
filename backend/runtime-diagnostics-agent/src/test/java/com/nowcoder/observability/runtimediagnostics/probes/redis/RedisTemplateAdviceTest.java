package com.nowcoder.observability.runtimediagnostics.probes.redis;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyDiagnosticsRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTemplateAdviceTest {

    @AfterEach
    void tearDown() {
        DependencyDiagnosticsRuntime.resetForTests();
    }

    @Test
    void commandNameFallsBackToExecute() {
        assertThat(RedisTemplateAdvice.commandName("execute")).isEqualTo("EXECUTE");
    }

    @Test
    void keyHashNeverReturnsRawKey() {
        String hash = RedisTemplateAdvice.hashKeyspace("user:token:secret");

        assertThat(hash).hasSize(16);
        assertThat(hash).doesNotContain("secret");
    }

    @Test
    void emittedDimensionsAvoidRedisKeyFieldNames() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DependencyDiagnosticsRuntime.initialize(redisConfig(),
                new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));
        String expectedNamespaceHash = RedisTemplateAdvice.hashKeyspace("user");

        RedisTemplateAdvice.onExit("execute", new Object[]{"user:token:secret"}, System.nanoTime(), null);
        DependencyDiagnosticsRuntime.reportSummary("redis", "redis_call_summary", 5);

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"redis.command\":\"EXECUTE\"")
                .contains("\"redis.namespace.hash\":\"" + expectedNamespaceHash + "\"")
                .doesNotContain("redis.key")
                .doesNotContain("user")
                .doesNotContain("token")
                .doesNotContain("secret");
    }

    private static DiagnosticsConfig redisConfig() {
        return new DiagnosticsConfig(true, List.of("redis"), List.of("*"), List.of(), 1.0, 20,
                Duration.ofSeconds(60), 50, 10_000, 100, Duration.ofSeconds(60), Duration.ofSeconds(60),
                500, 200, 100, 500,
                1.0, 1.0, 1.0, 1.0,
                20, 20, 20, 20,
                false);
    }
}
