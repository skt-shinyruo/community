# Runtime Diagnostics Agent Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Phase 2 dependency diagnostics probes for HTTP, JDBC/MyBatis, Redis, and Kafka on top of the Phase 1 `runtime-diagnostics-agent`.

**Architecture:** Reuse the Phase 1 probe framework and event logger. Add a dependency-call aggregation model shared by dependency probes, then add explicit opt-in probes for Spring WebClient HTTP calls, JDBC/MyBatis Statement execution, Spring Data RedisTemplate operations, and Spring Kafka KafkaTemplate sends. Dependency probes emit bounded diagnostic log events and summaries; they read existing trace context and never create tracing spans or collect payload data.

**Tech Stack:** Java 17, Maven, Byte Buddy Java Agent, JUnit 5, AssertJ, Java Management APIs, SHA-256 based sanitization helpers, dependency advice unit tests.

---

Source specs:

- `docs/superpowers/specs/2026-06-08-runtime-diagnostics-agent-blueprint-design.md`
- `docs/superpowers/specs/2026-06-08-runtime-diagnostics-agent-phase-2-design.md`

Prerequisite:

- Complete `docs/superpowers/plans/2026-06-08-runtime-diagnostics-agent-phase-1.md`.

Phase 2 does not change Community DDD business code. It only changes the generic agent module, deployment defaults, tests, and operations documentation.

## File Structure

- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/config/DiagnosticsConfig.java`: add dependency thresholds, sample rates, rate limits, and allowlist flags.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/config/DiagnosticsConfigLoader.java`: read `RUNTIME_DIAGNOSTICS_HTTP_*`, `JDBC_*`, `REDIS_*`, and `KAFKA_*`.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/DependencyTextSanitizer.java`: shared hash and low-cardinality text helpers.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/DependencyCallKey.java`: low-cardinality dependency identity.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/DependencyCallSnapshot.java`: immutable summary row.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/DependencyCallAggregator.java`: bounded call stats by dependency key.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/DependencyDiagnosticsRuntime.java`: shared hot-path event/summary runtime for dependency probes.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/http/HttpDiagnosticsProbe.java`: Spring WebClient instrumentation registration.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/http/HttpExchangeAdvice.java`: advice for `ExchangeFunction.exchange`.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/jdbc/JdbcDiagnosticsProbe.java`: JDBC Statement instrumentation registration.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/jdbc/JdbcStatementAdvice.java`: advice for Statement execution methods.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/redis/RedisDiagnosticsProbe.java`: Spring Data Redis instrumentation registration.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/redis/RedisTemplateAdvice.java`: advice for RedisTemplate execute methods.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/kafka/KafkaDiagnosticsProbe.java`: Spring Kafka instrumentation registration.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/kafka/KafkaTemplateAdvice.java`: advice for KafkaTemplate send methods.
- `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/**`: dependency aggregation tests.
- `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/http/**`: HTTP event tests.
- `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/jdbc/**`: JDBC event tests.
- `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/redis/**`: Redis event tests.
- `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/kafka/**`: Kafka event tests.
- `deploy/.env.single.example`: document Phase 2 probe settings.
- `deploy/.env.cluster.example`: document Phase 2 probe settings.
- `deploy/compose.runtime.services.single.yml`: expose Phase 2 settings.
- `deploy/compose.runtime.services.cluster.yml`: expose Phase 2 settings.
- `docs/handbook/operations.md`: add dependency troubleshooting guidance.
- `deploy/README.md`: add a short dependency-probe example.

## Task 1: Extend Configuration For Dependency Probes

**Files:**
- Modify: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/config/DiagnosticsConfig.java`
- Modify: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/config/DiagnosticsConfigLoader.java`
- Modify: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/config/DiagnosticsConfigLoaderTest.java`

- [x] **Step 1: Add failing dependency config tests**

Add this test to `DiagnosticsConfigLoaderTest`:

```java
@Test
void dependencyProbeSettingsAreLoadedFromEnvironment() {
    DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(), Map.of(
            "RUNTIME_DIAGNOSTICS_PROBES", "method,http,jdbc,redis,kafka",
            "RUNTIME_DIAGNOSTICS_HTTP_SLOW_THRESHOLD_MS", "501",
            "RUNTIME_DIAGNOSTICS_JDBC_SLOW_THRESHOLD_MS", "202",
            "RUNTIME_DIAGNOSTICS_REDIS_SLOW_THRESHOLD_MS", "103",
            "RUNTIME_DIAGNOSTICS_KAFKA_SLOW_THRESHOLD_MS", "504",
            "RUNTIME_DIAGNOSTICS_HTTP_SAMPLE_RATE", "0.5",
            "RUNTIME_DIAGNOSTICS_JDBC_SAMPLE_RATE", "0.25",
            "RUNTIME_DIAGNOSTICS_REDIS_SAMPLE_RATE", "0.75",
            "RUNTIME_DIAGNOSTICS_KAFKA_SAMPLE_RATE", "0.6",
            "RUNTIME_DIAGNOSTICS_HTTP_MAX_EVENTS_PER_SECOND", "11",
            "RUNTIME_DIAGNOSTICS_JDBC_MAX_EVENTS_PER_SECOND", "12",
            "RUNTIME_DIAGNOSTICS_REDIS_MAX_EVENTS_PER_SECOND", "13",
            "RUNTIME_DIAGNOSTICS_KAFKA_MAX_EVENTS_PER_SECOND", "14",
            "RUNTIME_DIAGNOSTICS_KAFKA_TOPIC_NAMES_ENABLED", "true"
    ));

    assertThat(config.probes()).containsExactly("method", "http", "jdbc", "redis", "kafka");
    assertThat(config.httpSlowThresholdMs()).isEqualTo(501);
    assertThat(config.jdbcSlowThresholdMs()).isEqualTo(202);
    assertThat(config.redisSlowThresholdMs()).isEqualTo(103);
    assertThat(config.kafkaSlowThresholdMs()).isEqualTo(504);
    assertThat(config.httpSampleRate()).isEqualTo(0.5);
    assertThat(config.jdbcSampleRate()).isEqualTo(0.25);
    assertThat(config.redisSampleRate()).isEqualTo(0.75);
    assertThat(config.kafkaSampleRate()).isEqualTo(0.6);
    assertThat(config.httpMaxEventsPerSecond()).isEqualTo(11);
    assertThat(config.jdbcMaxEventsPerSecond()).isEqualTo(12);
    assertThat(config.redisMaxEventsPerSecond()).isEqualTo(13);
    assertThat(config.kafkaMaxEventsPerSecond()).isEqualTo(14);
    assertThat(config.kafkaTopicNamesEnabled()).isTrue();
}
```

- [x] **Step 2: Run config test and verify it fails**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=DiagnosticsConfigLoaderTest
```

Expected: FAIL because dependency config accessors do not exist.

- [x] **Step 3: Extend `DiagnosticsConfig`**

Add these record components after the Phase 1 interval fields:

```java
long httpSlowThresholdMs,
long jdbcSlowThresholdMs,
long redisSlowThresholdMs,
long kafkaSlowThresholdMs,
double httpSampleRate,
double jdbcSampleRate,
double redisSampleRate,
double kafkaSampleRate,
int httpMaxEventsPerSecond,
int jdbcMaxEventsPerSecond,
int redisMaxEventsPerSecond,
int kafkaMaxEventsPerSecond,
boolean kafkaTopicNamesEnabled
```

Clamp them in the compact constructor:

```java
httpSlowThresholdMs = Math.max(0, httpSlowThresholdMs);
jdbcSlowThresholdMs = Math.max(0, jdbcSlowThresholdMs);
redisSlowThresholdMs = Math.max(0, redisSlowThresholdMs);
kafkaSlowThresholdMs = Math.max(0, kafkaSlowThresholdMs);
httpSampleRate = Math.max(0.0, Math.min(1.0, httpSampleRate));
jdbcSampleRate = Math.max(0.0, Math.min(1.0, jdbcSampleRate));
redisSampleRate = Math.max(0.0, Math.min(1.0, redisSampleRate));
kafkaSampleRate = Math.max(0.0, Math.min(1.0, kafkaSampleRate));
httpMaxEventsPerSecond = Math.max(0, httpMaxEventsPerSecond);
jdbcMaxEventsPerSecond = Math.max(0, jdbcMaxEventsPerSecond);
redisMaxEventsPerSecond = Math.max(0, redisMaxEventsPerSecond);
kafkaMaxEventsPerSecond = Math.max(0, kafkaMaxEventsPerSecond);
```

Update all existing `new DiagnosticsConfig(...)` calls in tests to pass:

```java
500, 200, 100, 500,
1.0, 1.0, 1.0, 1.0,
20, 20, 20, 20,
false
```

- [x] **Step 4: Extend `DiagnosticsConfigLoader`**

Add default values:

```java
values.put("httpSlowThresholdMs", configured(systemProperties, environment, "httpSlowThresholdMs", "RUNTIME_DIAGNOSTICS_HTTP_SLOW_THRESHOLD_MS", "500"));
values.put("jdbcSlowThresholdMs", configured(systemProperties, environment, "jdbcSlowThresholdMs", "RUNTIME_DIAGNOSTICS_JDBC_SLOW_THRESHOLD_MS", "200"));
values.put("redisSlowThresholdMs", configured(systemProperties, environment, "redisSlowThresholdMs", "RUNTIME_DIAGNOSTICS_REDIS_SLOW_THRESHOLD_MS", "100"));
values.put("kafkaSlowThresholdMs", configured(systemProperties, environment, "kafkaSlowThresholdMs", "RUNTIME_DIAGNOSTICS_KAFKA_SLOW_THRESHOLD_MS", "500"));
values.put("httpSampleRate", configured(systemProperties, environment, "httpSampleRate", "RUNTIME_DIAGNOSTICS_HTTP_SAMPLE_RATE", "1.0"));
values.put("jdbcSampleRate", configured(systemProperties, environment, "jdbcSampleRate", "RUNTIME_DIAGNOSTICS_JDBC_SAMPLE_RATE", "1.0"));
values.put("redisSampleRate", configured(systemProperties, environment, "redisSampleRate", "RUNTIME_DIAGNOSTICS_REDIS_SAMPLE_RATE", "1.0"));
values.put("kafkaSampleRate", configured(systemProperties, environment, "kafkaSampleRate", "RUNTIME_DIAGNOSTICS_KAFKA_SAMPLE_RATE", "1.0"));
values.put("httpMaxEventsPerSecond", configured(systemProperties, environment, "httpMaxEventsPerSecond", "RUNTIME_DIAGNOSTICS_HTTP_MAX_EVENTS_PER_SECOND", "20"));
values.put("jdbcMaxEventsPerSecond", configured(systemProperties, environment, "jdbcMaxEventsPerSecond", "RUNTIME_DIAGNOSTICS_JDBC_MAX_EVENTS_PER_SECOND", "20"));
values.put("redisMaxEventsPerSecond", configured(systemProperties, environment, "redisMaxEventsPerSecond", "RUNTIME_DIAGNOSTICS_REDIS_MAX_EVENTS_PER_SECOND", "20"));
values.put("kafkaMaxEventsPerSecond", configured(systemProperties, environment, "kafkaMaxEventsPerSecond", "RUNTIME_DIAGNOSTICS_KAFKA_MAX_EVENTS_PER_SECOND", "20"));
values.put("kafkaTopicNamesEnabled", configured(systemProperties, environment, "kafkaTopicNamesEnabled", "RUNTIME_DIAGNOSTICS_KAFKA_TOPIC_NAMES_ENABLED", "false"));
```

Add normalized keys for each property using the same `runtime.diagnostics.*` and env-style names.

- [x] **Step 5: Run config tests**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=DiagnosticsConfigLoaderTest
```

Expected: PASS.

- [x] **Step 6: Commit dependency config**

Run:

```bash
git add backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/config backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/config
git commit -m "feat: add dependency diagnostics config"
```

## Task 2: Add Shared Dependency Event Runtime

**Files:**
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/DependencyCallKey.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/DependencyTextSanitizer.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/DependencyCallSnapshot.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/DependencyCallAggregator.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/DependencyDiagnosticsRuntime.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/dependency/DependencyDiagnosticsRuntimeTest.java`

- [ ] **Step 1: Write dependency runtime tests**

Create `DependencyDiagnosticsRuntimeTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.dependency;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyDiagnosticsRuntimeTest {

    @AfterEach
    void tearDown() {
        DependencyDiagnosticsRuntime.resetForTests();
    }

    @Test
    void recordsSlowCallWithoutPayload() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DependencyDiagnosticsRuntime.initialize(config(), new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));

        DependencyDiagnosticsRuntime.recordCall(
                "jdbc",
                "jdbc_slow_call",
                "jdbc_call_summary",
                "threshold",
                new DependencyCallKey("jdbc", Map.of("db.operation", "select", "db.statement.hash", "abcdef")),
                250,
                200,
                true,
                Map.of("sql", "select * from user where password='secret'")
        );

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.category\":\"runtime_diagnostics\"")
                .contains("\"event.action\":\"jdbc_slow_call\"")
                .contains("\"diagnostic.probe\":\"jdbc\"")
                .contains("\"db.operation\":\"select\"")
                .contains("\"db.statement.hash\":\"abcdef\"")
                .contains("\"duration.ms\":250")
                .doesNotContain("password")
                .doesNotContain("select *");
    }

    @Test
    void summaryAggregatesCallsByDependencyKey() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DependencyDiagnosticsRuntime.initialize(config(), new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));
        DependencyCallKey key = new DependencyCallKey("redis", Map.of("redis.command", "GET"));

        DependencyDiagnosticsRuntime.recordCall("redis", "redis_slow_call", "redis_call_summary", "success", key, 10, 100, false, Map.of());
        DependencyDiagnosticsRuntime.recordCall("redis", "redis_slow_call", "redis_call_summary", "success", key, 30, 100, false, Map.of());
        DependencyDiagnosticsRuntime.reportSummary("redis", "redis_call_summary", 5);

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.action\":\"redis_call_summary\"")
                .contains("\"call.count\":2")
                .contains("\"duration.max.ms\":30");
    }

    private static DiagnosticsConfig config() {
        return new DiagnosticsConfig(true, List.of("jdbc", "redis"), List.of("*"), List.of(), 1.0, 20,
                Duration.ofSeconds(60), 50, 10_000, 100, Duration.ofSeconds(60), Duration.ofSeconds(60),
                500, 200, 100, 500,
                1.0, 1.0, 1.0, 1.0,
                20, 20, 20, 20,
                false);
    }
}
```

- [ ] **Step 2: Run dependency runtime tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=DependencyDiagnosticsRuntimeTest
```

Expected: FAIL because dependency runtime classes do not exist.

- [ ] **Step 3: Implement dependency text sanitizer, key, and snapshot**

Create `DependencyTextSanitizer.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.dependency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class DependencyTextSanitizer {

    private DependencyTextSanitizer() {
    }

    public static String hash16(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", digest[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public static String safeToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.isBlank() ? fallback : sanitized;
    }
}
```

Create `DependencyCallKey.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.dependency;

import java.util.LinkedHashMap;
import java.util.Map;

public record DependencyCallKey(String probe, Map<String, String> dimensions) {

    public DependencyCallKey {
        if (probe == null || probe.isBlank()) {
            throw new IllegalArgumentException("probe must not be blank");
        }
        dimensions = dimensions == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(dimensions));
    }
}
```

Create `DependencyCallSnapshot.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.dependency;

public record DependencyCallSnapshot(
        DependencyCallKey key,
        long count,
        long avgMs,
        long maxMs,
        long p95Ms,
        long errorCount
) {
}
```

- [ ] **Step 4: Implement aggregator**

Create `DependencyCallAggregator.java` using the existing method latency stats pattern. It should expose:

```java
public void record(DependencyCallKey key, long durationMs, boolean error)

public java.util.List<DependencyCallSnapshot> topSnapshots(String probe, int topN)

public long droppedKeys()
```

Ranking is by `duration.max.ms` descending, then `key.toString()` ascending.

- [ ] **Step 5: Implement runtime**

Create `DependencyDiagnosticsRuntime.java` with:

```java
public static void initialize(DiagnosticsConfig diagnosticsConfig, DiagnosticEventLogger eventLogger)

public static void recordCall(
        String probe,
        String slowAction,
        String summaryAction,
        String outcome,
        DependencyCallKey key,
        long durationMs,
        long thresholdMs,
        boolean error,
        Map<String, String> forbiddenDataForTests
)

public static void reportSummary(String probe, String summaryAction, int topN)

public static long thresholdMs(String probe)

public static void resetForTests()
```

`thresholdMs` must map probe names to config values:

```java
public static long thresholdMs(String probe) {
    DiagnosticsConfig currentConfig = config;
    if (currentConfig == null) {
        return Long.MAX_VALUE;
    }
    return switch (probe) {
        case "http" -> currentConfig.httpSlowThresholdMs();
        case "jdbc" -> currentConfig.jdbcSlowThresholdMs();
        case "redis" -> currentConfig.redisSlowThresholdMs();
        case "kafka" -> currentConfig.kafkaSlowThresholdMs();
        default -> Long.MAX_VALUE;
    };
}
```

The implementation must never log `forbiddenDataForTests`. That parameter exists only so tests can prove payload-like data is ignored.

Slow events use:

```java
DiagnosticEvent.builder(slowAction, "threshold", probe)
        .put("duration.ms", durationMs)
        .put("threshold.ms", thresholdMs)
```

and then copy `key.dimensions()` into the event.

Summary events use:

```java
DiagnosticEvent.builder(summaryAction, "success", probe)
        .put("call.count", snapshot.count())
        .put("duration.avg.ms", snapshot.avgMs())
        .put("duration.max.ms", snapshot.maxMs())
        .put("duration.p95.ms", snapshot.p95Ms())
        .put("error.count", snapshot.errorCount())
```

and then copy `snapshot.key().dimensions()` into the event.

- [ ] **Step 6: Run dependency runtime tests**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=DependencyDiagnosticsRuntimeTest
```

Expected: PASS.

- [ ] **Step 7: Commit shared dependency runtime**

Run:

```bash
git add backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/dependency backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/dependency
git commit -m "feat: add dependency diagnostics runtime"
```

## Task 3: Add JDBC/MyBatis Probe

**Files:**
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/jdbc/JdbcDiagnosticsProbe.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/jdbc/JdbcStatementAdvice.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/jdbc/JdbcStatementAdviceTest.java`
- Modify: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgent.java`

- [ ] **Step 1: Add JDBC advice test**

Create `JdbcStatementAdviceTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcStatementAdviceTest {

    @Test
    void normalizesSqlOperationAndHashWithoutBindValues() {
        JdbcStatementAdvice.JdbcCall call = JdbcStatementAdvice.describeSql("select * from user where password = 'secret'");

        assertThat(call.operation()).isEqualTo("select");
        assertThat(call.statementHash()).hasSize(16);
        assertThat(call.statementHash()).doesNotContain("secret");
    }

    @Test
    void unknownSqlUsesUnknownOperation() {
        JdbcStatementAdvice.JdbcCall call = JdbcStatementAdvice.describeSql("");

        assertThat(call.operation()).isEqualTo("unknown");
        assertThat(call.statementHash()).isEqualTo("unknown");
    }
}
```

- [ ] **Step 2: Run JDBC test and verify it fails**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=JdbcStatementAdviceTest
```

Expected: FAIL because JDBC advice does not exist.

- [ ] **Step 3: Implement JDBC advice**

Create `JdbcStatementAdvice.java` with static helpers and Byte Buddy advice. The helper must use SHA-256 and expose only a 16-character hex prefix:

```java
public record JdbcCall(String operation, String statementHash) {
}

public static JdbcCall describeSql(String sql) {
    if (sql == null || sql.isBlank()) {
        return new JdbcCall("unknown", "unknown");
    }
    String normalized = sql.replaceAll("'[^']*'", "?")
            .replaceAll("\\b\\d+\\b", "?")
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(java.util.Locale.ROOT);
    String operation = normalized.split(" ", 2)[0];
    if (!operation.matches("select|insert|update|delete|merge|call")) {
        operation = "unknown";
    }
    return new JdbcCall(operation, DependencyTextSanitizer.hash16(normalized));
}
```

Advice exit records:

```java
DependencyDiagnosticsRuntime.recordCall(
        "jdbc",
        "jdbc_slow_call",
        "jdbc_call_summary",
        thrown == null ? "success" : "error",
        new DependencyCallKey("jdbc", Map.of(
                "db.system", "jdbc",
                "db.operation", call.operation(),
                "db.statement.hash", call.statementHash()
        )),
        durationMs,
        DependencyDiagnosticsRuntime.thresholdMs("jdbc"),
        thrown != null,
        Map.of()
);
```

If the executed method has no SQL string argument, pass `null` to `describeSql`.

- [ ] **Step 4: Add JDBC probe registration**

Create `JdbcDiagnosticsProbe.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.jdbc;

import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;

public class JdbcDiagnosticsProbe implements Probe {

    @Override
    public String name() {
        return "jdbc";
    }

    @Override
    public void start(ProbeContext context) {
        // Byte Buddy registration is owned by RuntimeDiagnosticsAgent.
    }
}
```

Update `RuntimeDiagnosticsAgent` transformer registration so JDBC advice applies to non-JDK classes implementing `java.sql.Statement` and methods named:

```text
execute
executeQuery
executeUpdate
executeLargeUpdate
executeBatch
```

- [ ] **Step 5: Run JDBC tests**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=JdbcStatementAdviceTest,DependencyDiagnosticsRuntimeTest
```

Expected: PASS.

- [ ] **Step 6: Commit JDBC probe**

Run:

```bash
git add backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/jdbc backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/jdbc backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgent.java
git commit -m "feat: add jdbc diagnostics probe"
```

## Task 4: Add HTTP WebClient Probe

**Files:**
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/http/HttpDiagnosticsProbe.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/http/HttpExchangeAdvice.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/http/HttpExchangeAdviceTest.java`
- Modify: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgent.java`

- [ ] **Step 1: Add HTTP advice test**

Create `HttpExchangeAdviceTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExchangeAdviceTest {

    @Test
    void routeSanitizationDropsQueryString() {
        assertThat(HttpExchangeAdvice.sanitizeRoute("https://example.com/api/users?id=123&token=secret"))
                .isEqualTo("/api/users");
    }

    @Test
    void hostIsHashedAndNotReturnedRaw() {
        String hash = HttpExchangeAdvice.hashHost("internal-service.local");

        assertThat(hash).hasSize(16);
        assertThat(hash).doesNotContain("internal-service");
    }
}
```

- [ ] **Step 2: Run HTTP test and verify it fails**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=HttpExchangeAdviceTest
```

Expected: FAIL because HTTP advice does not exist.

- [ ] **Step 3: Implement HTTP advice helpers and event recording**

Create `HttpExchangeAdvice.java` with helpers:

```java
public static String hashHost(String host) {
    return DependencyTextSanitizer.hash16(host);
}

public static String sanitizeRoute(String rawUrl) {
    if (rawUrl == null || rawUrl.isBlank()) {
        return "unknown";
    }
    try {
        java.net.URI uri = java.net.URI.create(rawUrl);
        String path = uri.getPath();
        return path == null || path.isBlank() ? "/" : path;
    } catch (RuntimeException ignored) {
        int query = rawUrl.indexOf('?');
        String withoutQuery = query >= 0 ? rawUrl.substring(0, query) : rawUrl;
        return withoutQuery.isBlank() ? "unknown" : withoutQuery;
    }
}
```

Record events with dimensions:

```text
http.direction=outbound
http.method=<method or UNKNOWN>
http.route=<sanitized path>
network.peer.name.hash=<hash16 host>
```

Do not record raw URL, query string, headers, body, cookies, or authorization data.

- [ ] **Step 4: Add HTTP probe registration**

Create `HttpDiagnosticsProbe.java` with `name()` returning `http`.

Update `RuntimeDiagnosticsAgent` to instrument implementations of:

```text
org.springframework.web.reactive.function.client.ExchangeFunction
```

method:

```text
exchange
```

The advice measures method execution time and records an `http_slow_call` when duration crosses `config.httpSlowThresholdMs()`.

- [ ] **Step 5: Run HTTP tests**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=HttpExchangeAdviceTest,DependencyDiagnosticsRuntimeTest
```

Expected: PASS.

- [ ] **Step 6: Commit HTTP probe**

Run:

```bash
git add backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/http backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/http backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgent.java
git commit -m "feat: add http diagnostics probe"
```

## Task 5: Add Redis And Kafka Probes

**Files:**
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/redis/RedisDiagnosticsProbe.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/redis/RedisTemplateAdvice.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/kafka/KafkaDiagnosticsProbe.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/kafka/KafkaTemplateAdvice.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/redis/RedisTemplateAdviceTest.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/kafka/KafkaTemplateAdviceTest.java`
- Modify: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgent.java`

- [ ] **Step 1: Add Redis helper test**

Create `RedisTemplateAdviceTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.redis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTemplateAdviceTest {

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
}
```

- [ ] **Step 2: Add Kafka helper test**

Create `KafkaTemplateAdviceTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.kafka;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTemplateAdviceTest {

    @Test
    void topicIsHashedWhenTopicNamesAreDisabled() {
        assertThat(KafkaTemplateAdvice.destinationName("im-room-events", false))
                .hasSize(16)
                .doesNotContain("im-room-events");
    }

    @Test
    void topicCanBeEmittedWhenExplicitlyEnabled() {
        assertThat(KafkaTemplateAdvice.destinationName("im-room-events", true))
                .isEqualTo("im-room-events");
    }
}
```

- [ ] **Step 3: Run Redis and Kafka tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=RedisTemplateAdviceTest,KafkaTemplateAdviceTest
```

Expected: FAIL because Redis and Kafka advice classes do not exist.

- [ ] **Step 4: Implement Redis advice and probe**

Create `RedisTemplateAdvice.java` with:

```java
public static String commandName(String methodName) {
    return methodName == null || methodName.isBlank()
            ? "UNKNOWN"
            : methodName.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase(java.util.Locale.ROOT);
}

public static String hashKeyspace(String key) {
    return DependencyTextSanitizer.hash16(key);
}
```

Record dimensions:

```text
redis.command=<command>
redis.keyspace.hash=<hash if a safe keyspace can be derived, otherwise unknown>
```

Do not record Redis keys, values, serialized payloads, or Lua script text.

Create `RedisDiagnosticsProbe.java` with `name()` returning `redis`.

Update `RuntimeDiagnosticsAgent` to instrument:

```text
org.springframework.data.redis.core.RedisTemplate
```

methods:

```text
execute
executePipelined
executeWithStickyConnection
```

- [ ] **Step 5: Implement Kafka advice and probe**

Create `KafkaTemplateAdvice.java` with:

```java
    public static String destinationName(String topic, boolean topicNamesEnabled) {
        if (topic == null || topic.isBlank()) {
            return "unknown";
        }
        return topicNamesEnabled ? topic : DependencyTextSanitizer.hash16(topic);
    }
```

Record dimensions:

```text
messaging.operation=produce
messaging.destination.name=<topic or hash>
```

Do not record message payloads, keys, or arbitrary headers.

Create `KafkaDiagnosticsProbe.java` with `name()` returning `kafka`.

Update `RuntimeDiagnosticsAgent` to instrument:

```text
org.springframework.kafka.core.KafkaTemplate
```

method:

```text
send
```

- [ ] **Step 6: Run Redis and Kafka tests**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=RedisTemplateAdviceTest,KafkaTemplateAdviceTest,DependencyDiagnosticsRuntimeTest
```

Expected: PASS.

- [ ] **Step 7: Commit Redis and Kafka probes**

Run:

```bash
git add backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/redis backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/kafka backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/redis backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/kafka backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgent.java
git commit -m "feat: add redis and kafka diagnostics probes"
```

## Task 6: Wire Phase 2 Deployment Settings And Docs

**Files:**
- Modify: `deploy/compose.runtime.services.single.yml`
- Modify: `deploy/compose.runtime.services.cluster.yml`
- Modify: `deploy/.env.single.example`
- Modify: `deploy/.env.cluster.example`
- Modify: `deploy/README.md`
- Modify: `docs/handbook/operations.md`

- [ ] **Step 1: Add Phase 2 env defaults to compose and env examples**

Add these disabled-safe settings next to existing runtime diagnostics settings:

```dotenv
RUNTIME_DIAGNOSTICS_HTTP_SLOW_THRESHOLD_MS=500
RUNTIME_DIAGNOSTICS_JDBC_SLOW_THRESHOLD_MS=200
RUNTIME_DIAGNOSTICS_REDIS_SLOW_THRESHOLD_MS=100
RUNTIME_DIAGNOSTICS_KAFKA_SLOW_THRESHOLD_MS=500
RUNTIME_DIAGNOSTICS_HTTP_SAMPLE_RATE=1.0
RUNTIME_DIAGNOSTICS_JDBC_SAMPLE_RATE=1.0
RUNTIME_DIAGNOSTICS_REDIS_SAMPLE_RATE=1.0
RUNTIME_DIAGNOSTICS_KAFKA_SAMPLE_RATE=1.0
RUNTIME_DIAGNOSTICS_HTTP_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_JDBC_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_REDIS_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_KAFKA_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_KAFKA_TOPIC_NAMES_ENABLED=false
```

Keep `RUNTIME_DIAGNOSTICS_PROBES` default as:

```dotenv
RUNTIME_DIAGNOSTICS_PROBES=method,exception,thread,jvm
```

Dependency probes remain opt-in.

- [ ] **Step 2: Add docs examples**

In `deploy/README.md`, add:

```markdown
Enable dependency probes only for focused diagnostic runs:

```bash
RUNTIME_DIAGNOSTICS_ENABLED=true RUNTIME_DIAGNOSTICS_PROBES='method,exception,thread,jvm,jdbc,redis,kafka' ./deploy/deployment.sh up --topology single
```

Dependency probes emit summaries and slow-call events. They do not record HTTP bodies, SQL bind values, Redis keys or values, Kafka payloads, cookies, JWTs, or authorization headers.
```

In `docs/handbook/operations.md`, add Kibana filters:

```text
event.category : runtime_diagnostics
event.action : jdbc_call_summary
event.action : redis_call_summary
event.action : kafka_produce_summary
event.action : http_call_summary
diagnostic.probe : jdbc
trace.id : "<trace id>"
```

- [ ] **Step 3: Scan docs and deployment for Phase 2 settings**

Run:

```bash
rg -n 'RUNTIME_DIAGNOSTICS_(HTTP|JDBC|REDIS|KAFKA)_' deploy docs/handbook/operations.md
```

Expected: output includes compose files, env examples, deploy README, and operations docs.

- [ ] **Step 4: Commit docs and deployment settings**

Run:

```bash
git add deploy/compose.runtime.services.single.yml deploy/compose.runtime.services.cluster.yml deploy/.env.single.example deploy/.env.cluster.example deploy/README.md docs/handbook/operations.md
git commit -m "docs: document dependency diagnostics probes"
```

## Task 7: Final Phase 2 Verification

**Files:**
- Verify all files touched by Tasks 1-6.

- [ ] **Step 1: Run full runtime diagnostics agent verification**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent verify
```

Expected: PASS.

- [ ] **Step 2: Run deployment render verification**

Run:

```bash
./deploy/tests/observability_otel_default.sh
```

Expected: PASS.

- [ ] **Step 3: Check dependency probe names**

Run:

```bash
rg -n 'http_call_summary|jdbc_call_summary|redis_call_summary|kafka_produce_summary|RUNTIME_DIAGNOSTICS_HTTP_|RUNTIME_DIAGNOSTICS_JDBC_|RUNTIME_DIAGNOSTICS_REDIS_|RUNTIME_DIAGNOSTICS_KAFKA_' backend/runtime-diagnostics-agent deploy docs/handbook
```

Expected: output includes implementation files, tests, deploy settings, and docs.

- [ ] **Step 4: Check sensitive payload words are absent from event field names**

Run:

```bash
rg -n 'request.body|response.body|sql.bind|redis.key|redis.value|kafka.payload|authorization|cookie' backend/runtime-diagnostics-agent/src/main/java
```

Expected: no output.

- [ ] **Step 5: Review git status**

Run:

```bash
git status --short
```

Expected: clean working tree after all task commits.
