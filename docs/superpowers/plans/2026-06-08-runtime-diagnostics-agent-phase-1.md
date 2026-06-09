# Runtime Diagnostics Agent Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `method-profiler-agent` with `runtime-diagnostics-agent` and deliver Phase 1 runtime diagnostic probes for method latency, exceptions, threads, and JVM summaries.

**Architecture:** Keep the agent as a generic Byte Buddy based JVM `-javaagent` with no Spring or Community business dependency. Rename the module and package, introduce a small probe framework, migrate the existing method profiler into a method probe, and add scheduled thread/JVM probes plus exception events from method advice. All output uses the shared `event.category=runtime_diagnostics` event family and preserves existing OTel/MDC trace fields without creating synthetic traces.

**Tech Stack:** Java 17, Maven, Byte Buddy Java Agent, JUnit 5, AssertJ, Java Management APIs, shell deployment tests, Docker Compose config rendering.

---

Source specs:

- `docs/superpowers/specs/2026-06-08-runtime-diagnostics-agent-blueprint-design.md`
- `docs/superpowers/specs/2026-06-08-runtime-diagnostics-agent-phase-1-design.md`

Phase 1 does not implement HTTP, JDBC/MyBatis, Redis, or Kafka dependency probes. Those remain in `docs/superpowers/specs/2026-06-08-runtime-diagnostics-agent-phase-2-design.md`.

## File Structure

- `backend/pom.xml`: replace module `method-profiler-agent` with `runtime-diagnostics-agent`.
- `backend/runtime-diagnostics-agent/pom.xml`: renamed agent module with artifact/name `runtime-diagnostics-agent`, updated manifest `Premain-Class`, and the existing Byte Buddy/shade/failsafe setup.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgent.java`: `premain` entry point, config load, probe registry startup, Byte Buddy installation.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/config/DiagnosticsConfig.java`: immutable config record with global and Phase 1 probe settings.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/config/DiagnosticsConfigLoader.java`: reads `RUNTIME_DIAGNOSTICS_*`, runtime-diagnostics system properties, and agent args; ignores old `METHOD_PROFILER_*`.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/DiagnosticEvent.java`: structured event value with base fields and safe JSON conversion support.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/DiagnosticEventLogger.java`: writes diagnostic events to stdout JSON-compatible output, preserving the existing fallback behavior.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/DiagnosticRuntime.java`: shared hot-path runtime for method and exception recording, event queueing, rate limiting, and test reset.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/Probe.java`: probe lifecycle contract.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/ProbeContext.java`: shared services passed to probes.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/ProbeRegistry.java`: starts enabled probes independently and disables failed probes.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/trace/TraceContextReader.java`: renamed reflection-based OTel/MDC trace lookup.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/match/DiagnosticsMatcher.java`: renamed class/method include/exclude policy with hard excludes updated to the new package.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/method/**`: migrated method key, stats, histogram, summary reporter, advice, and method probe.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/exception/ExceptionDiagnosticsProbe.java`: exception probe marker/config integration; exception events are recorded by method advice.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/thread/ThreadDiagnosticsProbe.java`: scheduled thread state, deadlock, and lock-wait snapshots.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/jvm/JvmDiagnosticsProbe.java`: scheduled runtime, memory, GC, class loading, and thread-count summaries.
- `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/rate/TokenBucketRateLimiter.java`: moved existing rate limiter.
- `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/**`: renamed and expanded unit tests.
- `backend/runtime-diagnostics-agent/src/test/java/com/example/runtimediagnostics/integration/**`: forked JVM integration target.
- `deploy/Dockerfile.backend-service`: build and copy `/otel/runtime-diagnostics-agent.jar`.
- `backend/scripts/run-backend-service.sh`: enable the new agent only when `RUNTIME_DIAGNOSTICS_ENABLED=true`.
- `deploy/compose.runtime.services.single.yml`: replace method profiler env entries with `RUNTIME_DIAGNOSTICS_*`.
- `deploy/compose.runtime.services.cluster.yml`: replace method profiler env entries with `RUNTIME_DIAGNOSTICS_*`.
- `deploy/.env.single.example`: document safe runtime diagnostics defaults.
- `deploy/.env.cluster.example`: document safe runtime diagnostics defaults.
- `deploy/tests/observability_otel_default.sh`: assert runtime diagnostics defaults render and old method profiler env names are absent.
- `deploy/README.md`: replace optional method profiler docs with runtime diagnostics docs.
- `docs/handbook/operations.md`: add runtime diagnostics operator guidance.

## Task 1: Rename Module Identity

**Files:**
- Modify: `backend/pom.xml`
- Move: `backend/method-profiler-agent` to `backend/runtime-diagnostics-agent`
- Modify: `backend/runtime-diagnostics-agent/pom.xml`
- Move: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/methodprofiler/MethodProfilerAgent.java` to `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgent.java`
- Move: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/methodprofiler/MethodProfilerAgentSmokeTest.java` to `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgentSmokeTest.java`

- [x] **Step 1: Move the module directory**

Run:

```bash
git mv backend/method-profiler-agent backend/runtime-diagnostics-agent
```

Expected: `git status --short` shows a module rename and no deleted source tree left behind.

- [x] **Step 2: Replace the backend reactor module**

In `backend/pom.xml`, replace:

```xml
<module>method-profiler-agent</module>
```

with:

```xml
<module>runtime-diagnostics-agent</module>
```

- [x] **Step 3: Update the agent module POM identity**

In `backend/runtime-diagnostics-agent/pom.xml`, replace artifact metadata and manifest entries so these lines exist:

```xml
<artifactId>runtime-diagnostics-agent</artifactId>
<name>runtime-diagnostics-agent</name>
<description>Generic JVM runtime diagnostics Java agent</description>
```

Both manifest blocks must use:

```xml
<Premain-Class>com.nowcoder.observability.runtimediagnostics.RuntimeDiagnosticsAgent</Premain-Class>
```

The shade relocation should use:

```xml
<shadedPattern>com.nowcoder.observability.runtimediagnostics.shaded.net.bytebuddy</shadedPattern>
```

- [x] **Step 4: Rename the premain class and smoke test packages**

Move the class file to:

```text
backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgent.java
```

Use this class declaration:

```java
package com.nowcoder.observability.runtimediagnostics;

import java.lang.instrument.Instrumentation;

public final class RuntimeDiagnosticsAgent {

    private RuntimeDiagnosticsAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        if (instrumentation == null) {
            return;
        }
    }
}
```

Move the smoke test to:

```text
backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgentSmokeTest.java
```

Use this test:

```java
package com.nowcoder.observability.runtimediagnostics;

import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDiagnosticsAgentSmokeTest {

    @Test
    void exposesPremainEntryPoint() throws Exception {
        assertThat(RuntimeDiagnosticsAgent.class.getMethod("premain", String.class, Instrumentation.class))
                .isNotNull();
    }
}
```

- [x] **Step 5: Run the rename smoke test**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=RuntimeDiagnosticsAgentSmokeTest
```

Expected: PASS.

- [x] **Step 6: Verify the renamed shaded jar manifest**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent -DskipTests package
tmp_manifest="$(mktemp -d)"
cd "${tmp_manifest}"
jar xf /home/feng/code/project/community/backend/runtime-diagnostics-agent/target/runtime-diagnostics-agent-0.0.1-SNAPSHOT.jar META-INF/MANIFEST.MF
grep -F 'Premain-Class: com.nowcoder.observability.runtimediagnostics.RuntimeDiagnosticsAgent' META-INF/MANIFEST.MF
cd /home/feng/code/project/community/backend
rm -rf "${tmp_manifest}"
```

Expected: `grep` prints the `Premain-Class` line.

- [x] **Step 7: Commit the rename skeleton**

Run:

```bash
git add backend/pom.xml backend/runtime-diagnostics-agent
git commit -m "refactor: rename runtime diagnostics agent module"
```

## Task 2: Add Runtime Diagnostics Configuration

**Files:**
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/config/DiagnosticsConfig.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/config/DiagnosticsConfigLoader.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/config/DiagnosticsConfigLoaderTest.java`

- [x] **Step 1: Write config loader tests**

Create `DiagnosticsConfigLoaderTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticsConfigLoaderTest {

    @Test
    void defaultsAreDisabledAndPhaseOneProbesAreBounded() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(), Map.of());

        assertThat(config.enabled()).isFalse();
        assertThat(config.probes()).containsExactly("method", "exception", "thread", "jvm");
        assertThat(config.includes()).containsExactly("*");
        assertThat(config.excludes()).isEmpty();
        assertThat(config.sampleRate()).isEqualTo(1.0);
        assertThat(config.maxEventsPerSecond()).isEqualTo(20);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.topN()).isEqualTo(50);
        assertThat(config.maxTrackedKeys()).isEqualTo(10_000);
        assertThat(config.methodSlowThresholdMs()).isEqualTo(100);
        assertThat(config.threadSnapshotInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.jvmSummaryInterval()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void runtimeDiagnosticsEnvironmentOverridesDefaults() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(), Map.of(
                "RUNTIME_DIAGNOSTICS_ENABLED", "true",
                "RUNTIME_DIAGNOSTICS_PROBES", "method,jvm",
                "RUNTIME_DIAGNOSTICS_INCLUDES", "com.example.*,org.demo.Service",
                "RUNTIME_DIAGNOSTICS_EXCLUDES", "com.example.internal.*",
                "RUNTIME_DIAGNOSTICS_SAMPLE_RATE", "0.25",
                "RUNTIME_DIAGNOSTICS_MAX_EVENTS_PER_SECOND", "7",
                "RUNTIME_DIAGNOSTICS_SUMMARY_INTERVAL", "30s",
                "RUNTIME_DIAGNOSTICS_TOP_N", "25",
                "RUNTIME_DIAGNOSTICS_MAX_TRACKED_KEYS", "99",
                "RUNTIME_DIAGNOSTICS_METHOD_SLOW_THRESHOLD_MS", "250",
                "RUNTIME_DIAGNOSTICS_THREAD_SNAPSHOT_INTERVAL", "15s",
                "RUNTIME_DIAGNOSTICS_JVM_SUMMARY_INTERVAL", "20s"
        ));

        assertThat(config.enabled()).isTrue();
        assertThat(config.probes()).containsExactly("method", "jvm");
        assertThat(config.includes()).containsExactly("com.example.*", "org.demo.Service");
        assertThat(config.excludes()).containsExactly("com.example.internal.*");
        assertThat(config.sampleRate()).isEqualTo(0.25);
        assertThat(config.maxEventsPerSecond()).isEqualTo(7);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.topN()).isEqualTo(25);
        assertThat(config.maxTrackedKeys()).isEqualTo(99);
        assertThat(config.methodSlowThresholdMs()).isEqualTo(250);
        assertThat(config.threadSnapshotInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(config.jvmSummaryInterval()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void oldMethodProfilerEnvironmentNamesAreIgnored() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(), Map.of(
                "METHOD_PROFILER_ENABLED", "true",
                "METHOD_PROFILER_INCLUDES", "com.legacy.*",
                "METHOD_PROFILER_SLOW_THRESHOLD_MS", "1"
        ));

        assertThat(config.enabled()).isFalse();
        assertThat(config.includes()).containsExactly("*");
        assertThat(config.methodSlowThresholdMs()).isEqualTo(100);
    }

    @Test
    void agentArgsOverrideEnvironmentAndSupportCsvContinuations() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load(
                "enabled=true,probes=method,exception,includes=com.agent.*,org.demo.Service,methodSlowThresholdMs=333,summaryInterval=5s,topN=3,sampleRate=0.5,maxEventsPerSecond=2,maxTrackedKeys=10",
                Map.of(),
                Map.of("RUNTIME_DIAGNOSTICS_ENABLED", "false", "RUNTIME_DIAGNOSTICS_INCLUDES", "com.env.*")
        );

        assertThat(config.enabled()).isTrue();
        assertThat(config.probes()).containsExactly("method", "exception");
        assertThat(config.includes()).containsExactly("com.agent.*", "org.demo.Service");
        assertThat(config.methodSlowThresholdMs()).isEqualTo(333);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.topN()).isEqualTo(3);
        assertThat(config.sampleRate()).isEqualTo(0.5);
        assertThat(config.maxEventsPerSecond()).isEqualTo(2);
        assertThat(config.maxTrackedKeys()).isEqualTo(10);
    }

    @Test
    void systemPropertiesUseRuntimeDiagnosticsPrefix() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(
                "runtime.diagnostics.enabled", "true",
                "runtime.diagnostics.probes", "thread,jvm",
                "runtime.diagnostics.topN", "9"
        ), Map.of());

        assertThat(config.enabled()).isTrue();
        assertThat(config.probes()).containsExactly("thread", "jvm");
        assertThat(config.topN()).isEqualTo(9);
    }
}
```

- [x] **Step 2: Run config tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=DiagnosticsConfigLoaderTest
```

Expected: FAIL because `DiagnosticsConfig` and `DiagnosticsConfigLoader` do not exist yet.

- [x] **Step 3: Implement `DiagnosticsConfig`**

Create `DiagnosticsConfig.java`:

```java
package com.nowcoder.observability.runtimediagnostics.config;

import java.time.Duration;
import java.util.List;

public record DiagnosticsConfig(
        boolean enabled,
        List<String> probes,
        List<String> includes,
        List<String> excludes,
        double sampleRate,
        int maxEventsPerSecond,
        Duration summaryInterval,
        int topN,
        int maxTrackedKeys,
        long methodSlowThresholdMs,
        Duration threadSnapshotInterval,
        Duration jvmSummaryInterval
) {

    private static final List<String> DEFAULT_PROBES = List.of("method", "exception", "thread", "jvm");

    public DiagnosticsConfig {
        probes = List.copyOf(probes == null || probes.isEmpty() ? DEFAULT_PROBES : probes);
        includes = List.copyOf(includes == null || includes.isEmpty() ? List.of("*") : includes);
        excludes = List.copyOf(excludes == null ? List.of() : excludes);
        sampleRate = Math.max(0.0, Math.min(1.0, sampleRate));
        maxEventsPerSecond = Math.max(0, maxEventsPerSecond);
        summaryInterval = positiveOrDefault(summaryInterval, Duration.ofSeconds(60));
        topN = Math.max(1, topN);
        maxTrackedKeys = Math.max(1, maxTrackedKeys);
        methodSlowThresholdMs = Math.max(0, methodSlowThresholdMs);
        threadSnapshotInterval = positiveOrDefault(threadSnapshotInterval, Duration.ofSeconds(60));
        jvmSummaryInterval = positiveOrDefault(jvmSummaryInterval, Duration.ofSeconds(60));
    }

    public boolean probeEnabled(String probe) {
        return probes.contains(probe);
    }

    private static Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
```

- [x] **Step 4: Implement `DiagnosticsConfigLoader`**

Create `DiagnosticsConfigLoader.java` by adapting the existing config loader and using only the new names:

```java
package com.nowcoder.observability.runtimediagnostics.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class DiagnosticsConfigLoader {

    private DiagnosticsConfigLoader() {
    }

    public static DiagnosticsConfig load(String agentArgs) {
        return load(agentArgs, propertiesMap(System.getProperties()), System.getenv());
    }

    static DiagnosticsConfig load(String agentArgs, Map<String, String> systemProperties, Map<String, String> environment) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("enabled", configured(systemProperties, environment, "enabled", "RUNTIME_DIAGNOSTICS_ENABLED", "false"));
        values.put("probes", configured(systemProperties, environment, "probes", "RUNTIME_DIAGNOSTICS_PROBES", "method,exception,thread,jvm"));
        values.put("includes", configured(systemProperties, environment, "includes", "RUNTIME_DIAGNOSTICS_INCLUDES", "*"));
        values.put("excludes", configured(systemProperties, environment, "excludes", "RUNTIME_DIAGNOSTICS_EXCLUDES", ""));
        values.put("sampleRate", configured(systemProperties, environment, "sampleRate", "RUNTIME_DIAGNOSTICS_SAMPLE_RATE", "1.0"));
        values.put("maxEventsPerSecond", configured(systemProperties, environment, "maxEventsPerSecond", "RUNTIME_DIAGNOSTICS_MAX_EVENTS_PER_SECOND", "20"));
        values.put("summaryInterval", configured(systemProperties, environment, "summaryInterval", "RUNTIME_DIAGNOSTICS_SUMMARY_INTERVAL", "60s"));
        values.put("topN", configured(systemProperties, environment, "topN", "RUNTIME_DIAGNOSTICS_TOP_N", "50"));
        values.put("maxTrackedKeys", configured(systemProperties, environment, "maxTrackedKeys", "RUNTIME_DIAGNOSTICS_MAX_TRACKED_KEYS", "10000"));
        values.put("methodSlowThresholdMs", configured(systemProperties, environment, "methodSlowThresholdMs", "RUNTIME_DIAGNOSTICS_METHOD_SLOW_THRESHOLD_MS", "100"));
        values.put("threadSnapshotInterval", configured(systemProperties, environment, "threadSnapshotInterval", "RUNTIME_DIAGNOSTICS_THREAD_SNAPSHOT_INTERVAL", "60s"));
        values.put("jvmSummaryInterval", configured(systemProperties, environment, "jvmSummaryInterval", "RUNTIME_DIAGNOSTICS_JVM_SUMMARY_INTERVAL", "60s"));
        parseAgentArgs(agentArgs).forEach(values::put);

        return new DiagnosticsConfig(
                Boolean.parseBoolean(values.get("enabled")),
                csv(values.get("probes")),
                csv(values.get("includes")),
                csv(values.get("excludes")),
                parseDouble(values.get("sampleRate"), 1.0),
                parseInt(values.get("maxEventsPerSecond"), 20),
                parseDuration(values.get("summaryInterval"), Duration.ofSeconds(60)),
                parseInt(values.get("topN"), 50),
                parseInt(values.get("maxTrackedKeys"), 10_000),
                parseLong(values.get("methodSlowThresholdMs"), 100),
                parseDuration(values.get("threadSnapshotInterval"), Duration.ofSeconds(60)),
                parseDuration(values.get("jvmSummaryInterval"), Duration.ofSeconds(60))
        );
    }

    private static Map<String, String> parseAgentArgs(String agentArgs) {
        Map<String, String> result = new LinkedHashMap<>();
        if (agentArgs == null || agentArgs.isBlank()) {
            return result;
        }
        String currentKey = null;
        for (String pair : agentArgs.split(",")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                if (currentKey != null && acceptsCsvContinuation(currentKey) && !pair.isBlank()) {
                    result.compute(currentKey, (key, value) -> value == null || value.isBlank()
                            ? pair.trim()
                            : value + "," + pair.trim());
                }
                continue;
            }
            String key = normalizeKey(pair.substring(0, index).trim());
            String value = pair.substring(index + 1).trim();
            if (!key.isEmpty()) {
                result.put(key, value);
                currentKey = key;
            }
        }
        return result;
    }

    private static boolean acceptsCsvContinuation(String key) {
        return "probes".equals(key) || "includes".equals(key) || "excludes".equals(key);
    }

    private static String normalizeKey(String key) {
        return switch (key) {
            case "enabled", "runtime.diagnostics.enabled", "RUNTIME_DIAGNOSTICS_ENABLED" -> "enabled";
            case "probes", "runtime.diagnostics.probes", "RUNTIME_DIAGNOSTICS_PROBES" -> "probes";
            case "includes", "runtime.diagnostics.includes", "RUNTIME_DIAGNOSTICS_INCLUDES" -> "includes";
            case "excludes", "runtime.diagnostics.excludes", "RUNTIME_DIAGNOSTICS_EXCLUDES" -> "excludes";
            case "sampleRate", "runtime.diagnostics.sampleRate", "RUNTIME_DIAGNOSTICS_SAMPLE_RATE" -> "sampleRate";
            case "maxEventsPerSecond", "runtime.diagnostics.maxEventsPerSecond", "RUNTIME_DIAGNOSTICS_MAX_EVENTS_PER_SECOND" -> "maxEventsPerSecond";
            case "summaryInterval", "runtime.diagnostics.summaryInterval", "RUNTIME_DIAGNOSTICS_SUMMARY_INTERVAL" -> "summaryInterval";
            case "topN", "runtime.diagnostics.topN", "RUNTIME_DIAGNOSTICS_TOP_N" -> "topN";
            case "maxTrackedKeys", "runtime.diagnostics.maxTrackedKeys", "RUNTIME_DIAGNOSTICS_MAX_TRACKED_KEYS" -> "maxTrackedKeys";
            case "methodSlowThresholdMs", "runtime.diagnostics.methodSlowThresholdMs", "RUNTIME_DIAGNOSTICS_METHOD_SLOW_THRESHOLD_MS" -> "methodSlowThresholdMs";
            case "threadSnapshotInterval", "runtime.diagnostics.threadSnapshotInterval", "RUNTIME_DIAGNOSTICS_THREAD_SNAPSHOT_INTERVAL" -> "threadSnapshotInterval";
            case "jvmSummaryInterval", "runtime.diagnostics.jvmSummaryInterval", "RUNTIME_DIAGNOSTICS_JVM_SUMMARY_INTERVAL" -> "jvmSummaryInterval";
            default -> key;
        };
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static String configured(
            Map<String, String> systemProperties,
            Map<String, String> environment,
            String propertyName,
            String environmentName,
            String fallback
    ) {
        return first(
                systemProperties.get("runtime.diagnostics." + propertyName),
                systemProperties.get(environmentName),
                environment.get(environmentName),
                fallback
        );
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Duration parseDuration(String value, Duration fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
            }
            if (normalized.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            if (normalized.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            return Duration.parse(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Map<String, String> propertiesMap(Properties properties) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            result.put(name, properties.getProperty(name));
        }
        return result;
    }
}
```

- [x] **Step 5: Run config tests**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=DiagnosticsConfigLoaderTest
```

Expected: PASS.

- [x] **Step 6: Commit config**

Run:

```bash
git add backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/config backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/config
git commit -m "feat: add runtime diagnostics config"
```

## Task 3: Add Core Event And Probe Framework

**Files:**
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/DiagnosticEvent.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/DiagnosticEventLogger.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/Probe.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/ProbeContext.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/ProbeRegistry.java`
- Move: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/trace/TraceContextReader.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/core/DiagnosticEventLoggerTest.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/core/ProbeRegistryTest.java`

- [x] **Step 1: Write core tests**

Create `DiagnosticEventLoggerTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticEventLoggerTest {

    @Test
    void writesRuntimeDiagnosticsBaseFieldsAndTraceFields() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiagnosticEventLogger logger = new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "community-app");

        logger.log(DiagnosticEvent.builder("method_slow_call", "threshold", "method")
                .put("duration.ms", 123)
                .putTraceFields(Map.of("trace.id", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .build());

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.category\":\"runtime_diagnostics\"")
                .contains("\"event.action\":\"method_slow_call\"")
                .contains("\"event.outcome\":\"threshold\"")
                .contains("\"diagnostic.agent.name\":\"runtime-diagnostics-agent\"")
                .contains("\"diagnostic.probe\":\"method\"")
                .contains("\"service.name\":\"community-app\"")
                .contains("\"trace.id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"")
                .contains("\"duration.ms\":123");
    }

    @Test
    void escapesJsonControlCharacters() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiagnosticEventLogger logger = new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "svc");

        logger.log(DiagnosticEvent.builder("exception_observed", "error", "exception")
                .put("exception.type", "java.lang.IllegalStateException")
                .put("method.name", "line\nbreak")
                .build());

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"method.name\":\"line\\nbreak\"");
    }
}
```

Create `ProbeRegistryTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.core;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProbeRegistryTest {

    @Test
    void startsEnabledProbesAndContinuesAfterFailure() {
        List<String> started = new ArrayList<>();
        Probe failing = new RecordingProbe("method", started, true);
        Probe healthy = new RecordingProbe("jvm", started, false);
        Probe disabled = new RecordingProbe("thread", started, false);
        ProbeRegistry registry = new ProbeRegistry(List.of(failing, healthy, disabled));

        registry.startEnabled(config(List.of("method", "jvm")), ProbeContext.noop());

        assertThat(started).containsExactly("method", "jvm");
        assertThat(registry.disabledProbeNames()).containsExactly("method");
    }

    private static DiagnosticsConfig config(List<String> probes) {
        return new DiagnosticsConfig(true, probes, List.of("*"), List.of(), 1.0, 20,
                Duration.ofSeconds(60), 50, 10_000, 100, Duration.ofSeconds(60), Duration.ofSeconds(60));
    }

    private record RecordingProbe(String name, List<String> started, boolean fail) implements Probe {
        @Override
        public void start(ProbeContext context) {
            started.add(name);
            if (fail) {
                throw new IllegalStateException("probe failed");
            }
        }
    }
}
```

- [x] **Step 2: Run core tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=DiagnosticEventLoggerTest,ProbeRegistryTest
```

Expected: FAIL because core classes do not exist yet.

- [x] **Step 3: Implement `DiagnosticEvent` and `DiagnosticEventLogger`**

Create `DiagnosticEvent.java` with:

```java
package com.nowcoder.observability.runtimediagnostics.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record DiagnosticEvent(Map<String, Object> fields) {

    public static Builder builder(String action, String outcome, String probe) {
        return new Builder(action, outcome, probe);
    }

    public static final class Builder {
        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Builder(String action, String outcome, String probe) {
            fields.put("@timestamp", Instant.now().toString());
            fields.put("event.category", "runtime_diagnostics");
            fields.put("event.action", action);
            fields.put("event.outcome", outcome);
            fields.put("diagnostic.agent.name", "runtime-diagnostics-agent");
            fields.put("diagnostic.probe", probe);
        }

        public Builder put(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                fields.put(key, value);
            }
            return this;
        }

        public Builder putTraceFields(Map<String, String> traceFields) {
            if (traceFields != null) {
                traceFields.forEach((key, value) -> {
                    if (value != null && !value.isBlank()) {
                        fields.put(key, value);
                    }
                });
            }
            return this;
        }

        public DiagnosticEvent build() {
            return new DiagnosticEvent(Map.copyOf(fields));
        }
    }
}
```

Create `DiagnosticEventLogger.java` by adapting the existing JSON writer and using `runtime.diagnostics.service.name` before OTel names:

```java
package com.nowcoder.observability.runtimediagnostics.core;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class DiagnosticEventLogger {

    private final PrintStream output;
    private final String serviceName;

    public DiagnosticEventLogger() {
        this(System.out);
    }

    public DiagnosticEventLogger(PrintStream output) {
        this(output, resolveServiceName());
    }

    public DiagnosticEventLogger(PrintStream output, String serviceName) {
        this.output = output;
        this.serviceName = serviceName == null || serviceName.isBlank() ? "unknown" : serviceName;
    }

    public void log(DiagnosticEvent event) {
        if (event == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("service.name", serviceName);
        fields.putAll(event.fields());
        write(fields);
    }

    private void write(Map<String, Object> fields) {
        try {
            output.println(toJson(fields));
        } catch (RuntimeException ignored) {
        }
    }

    private String toJson(Map<String, Object> fields) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return builder.append('}').toString();
    }

    private String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String resolveServiceName() {
        String property = System.getProperty("runtime.diagnostics.service.name");
        if (property != null && !property.isBlank()) {
            return property;
        }
        property = System.getProperty("otel.service.name");
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv("OTEL_SERVICE_NAME");
        if (environment != null && !environment.isBlank()) {
            return environment;
        }
        environment = System.getenv("SERVICE_NAME");
        if (environment != null && !environment.isBlank()) {
            return environment;
        }
        return "unknown";
    }
}
```

- [x] **Step 4: Implement probe framework classes**

Create `Probe.java`:

```java
package com.nowcoder.observability.runtimediagnostics.core;

public interface Probe {

    String name();

    void start(ProbeContext context);

    default void stop() {
    }
}
```

Create `ProbeContext.java`:

```java
package com.nowcoder.observability.runtimediagnostics.core;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.trace.TraceContextReader;

public record ProbeContext(
        DiagnosticsConfig config,
        DiagnosticEventLogger logger,
        TraceContextReader traceContextReader
) {

    public static ProbeContext noop() {
        return new ProbeContext(null, new DiagnosticEventLogger(System.out, "noop"), new TraceContextReader());
    }
}
```

Create `ProbeRegistry.java`:

```java
package com.nowcoder.observability.runtimediagnostics.core;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;

import java.util.ArrayList;
import java.util.List;

public class ProbeRegistry {

    private final List<Probe> probes;
    private final List<String> disabledProbeNames = new ArrayList<>();

    public ProbeRegistry(List<Probe> probes) {
        this.probes = List.copyOf(probes);
    }

    public void startEnabled(DiagnosticsConfig config, ProbeContext context) {
        disabledProbeNames.clear();
        for (Probe probe : probes) {
            if (config == null || !config.probeEnabled(probe.name())) {
                continue;
            }
            try {
                probe.start(context);
            } catch (Throwable ex) {
                disabledProbeNames.add(probe.name());
            }
        }
    }

    public List<String> disabledProbeNames() {
        return List.copyOf(disabledProbeNames);
    }
}
```

- [x] **Step 5: Move trace reader to the new package**

Move the existing `TraceContextReader` implementation to:

```text
backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/trace/TraceContextReader.java
```

Update its package declaration:

```java
package com.nowcoder.observability.runtimediagnostics.trace;
```

Keep the existing reflection lookup behavior.

- [x] **Step 6: Run core tests**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=DiagnosticEventLoggerTest,ProbeRegistryTest
```

Expected: PASS.

- [x] **Step 7: Commit core framework**

Run:

```bash
git add backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/trace backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/core
git commit -m "feat: add runtime diagnostics probe framework"
```

## Task 4: Migrate Method Probe

**Files:**
- Move: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/model/MethodKey.java` to `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/method/MethodKey.java`
- Move: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/model/MethodSnapshot.java` to `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/method/MethodSnapshot.java`
- Move: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/stats/LatencyHistogram.java` to `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/method/LatencyHistogram.java`
- Move: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/stats/MethodLatencyStats.java` to `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/method/MethodLatencyStats.java`
- Move: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/stats/MethodLatencyAggregator.java` to `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/method/MethodLatencyAggregator.java`
- Move: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/schedule/SummaryReporter.java` to `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/method/MethodSummaryReporter.java`
- Move: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/instrument/ProfilingAdvice.java` to `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/method/MethodTimingAdvice.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/method/MethodDiagnosticsProbe.java`
- Move: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/rate/TokenBucketRateLimiter.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/match/DiagnosticsMatcher.java`
- Modify: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/DiagnosticRuntime.java`
- Test: renamed method tests under `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/method`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/match/DiagnosticsMatcherTest.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/core/DiagnosticRuntimeTest.java`

- [x] **Step 1: Move method support classes into `probes/method` and `rate`**

Use `git mv` for the existing model, stats, schedule, instrument, and rate files so history is preserved. Update package declarations to:

```java
package com.nowcoder.observability.runtimediagnostics.probes.method;
```

for method-specific files, and:

```java
package com.nowcoder.observability.runtimediagnostics.rate;
```

for `TokenBucketRateLimiter`.

- [x] **Step 2: Write matcher test for new hard excludes**

Create `DiagnosticsMatcherTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.match;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticsMatcherTest {

    @Test
    void excludesAgentPackageAndJdkPackages() {
        DiagnosticsMatcher matcher = new DiagnosticsMatcher(config(List.of("*"), List.of()));

        assertThat(matcher.shouldInstrumentClass("com.nowcoder.observability.runtimediagnostics.core.DiagnosticRuntime")).isFalse();
        assertThat(matcher.shouldInstrumentClass("java.lang.String")).isFalse();
        assertThat(matcher.shouldInstrumentClass("com.example.Service")).isTrue();
    }

    @Test
    void userExcludesOverrideIncludes() {
        DiagnosticsMatcher matcher = new DiagnosticsMatcher(config(List.of("com.example.*"), List.of("com.example.internal.*")));

        assertThat(matcher.shouldInstrumentClass("com.example.Service")).isTrue();
        assertThat(matcher.shouldInstrumentClass("com.example.internal.SecretService")).isFalse();
    }

    private static DiagnosticsConfig config(List<String> includes, List<String> excludes) {
        return new DiagnosticsConfig(true, List.of("method"), includes, excludes, 1.0, 20,
                Duration.ofSeconds(60), 50, 10_000, 100, Duration.ofSeconds(60), Duration.ofSeconds(60));
    }
}
```

- [x] **Step 3: Implement `DiagnosticsMatcher`**

Create `DiagnosticsMatcher.java` by adapting the existing matcher. The hard excludes must include:

```java
private static final List<String> HARD_EXCLUDES = List.of(
        "java.*",
        "javax.*",
        "jakarta.*",
        "sun.*",
        "jdk.*",
        "org.slf4j.*",
        "ch.qos.logback.*",
        "net.bytebuddy.*",
        "com.nowcoder.observability.runtimediagnostics.*"
);
```

Keep `shouldInstrumentClass`, `shouldInstrumentMethod`, and the existing glob matching behavior.

- [x] **Step 4: Write runtime method recording test**

Create or update `DiagnosticRuntimeTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.core;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.probes.method.MethodLatencyAggregator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticRuntimeTest {

    @AfterEach
    void tearDown() {
        DiagnosticRuntime.resetForTests();
    }

    @Test
    void recordsMethodDurationWhenMethodProbeIsEnabled() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        DiagnosticRuntime.initialize(config(1.0), aggregator, new DiagnosticEventLogger(System.out, "test"));

        DiagnosticRuntime.recordMethod("com.example.Service", "work", "()V", 125);

        assertThat(aggregator.topSnapshots(1))
                .singleElement()
                .extracting(snapshot -> snapshot.key().className(), snapshot -> snapshot.key().methodName(), snapshot -> snapshot.maxMs())
                .containsExactly("com.example.Service", "work", 125L);
    }

    @Test
    void samplingZeroDropsMethodDurations() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        DiagnosticRuntime.initialize(config(0.0), aggregator, new DiagnosticEventLogger(System.out, "test"));

        DiagnosticRuntime.recordMethod("com.example.Service", "work", "()V", 125);

        assertThat(aggregator.topSnapshots(1)).isEmpty();
    }

    private static DiagnosticsConfig config(double sampleRate) {
        return new DiagnosticsConfig(true, List.of("method"), List.of("*"), List.of(), sampleRate, 20,
                Duration.ofSeconds(60), 50, 10, 1_000, Duration.ofSeconds(60), Duration.ofSeconds(60));
    }
}
```

- [x] **Step 5: Implement method runtime and logger events**

Adapt the existing `ProfilerRuntime` into `DiagnosticRuntime` with these public methods:

```java
public static void initialize(
        DiagnosticsConfig diagnosticsConfig,
        MethodLatencyAggregator latencyAggregator,
        DiagnosticEventLogger eventLogger
)

public static void recordMethod(String className, String methodName, String descriptor, long durationMs)

public static void resetForTests()
```

Slow-call events must use:

```java
DiagnosticEvent.builder("method_slow_call", "threshold", "method")
        .put("method.class", key.className())
        .put("method.name", key.methodName())
        .put("method.signature.hash", key.signatureHash())
        .put("duration.ms", durationMs)
        .put("threshold.ms", currentConfig.methodSlowThresholdMs())
        .putTraceFields(currentTraceReader.currentTraceFields())
        .build()
```

The slow-call reporter thread name should be:

```text
runtime-diagnostics-method-slow-calls
```

- [x] **Step 6: Update method summary reporter**

`MethodSummaryReporter.reportOnce()` must emit one event per method snapshot:

```java
DiagnosticEvent.builder("method_latency_summary", "success", "method")
        .put("method.class", snapshot.key().className())
        .put("method.name", snapshot.key().methodName())
        .put("method.signature.hash", snapshot.key().signatureHash())
        .put("method.invocation.count", snapshot.count())
        .put("duration.avg.ms", snapshot.avgMs())
        .put("duration.max.ms", snapshot.maxMs())
        .put("duration.p95.ms", snapshot.p95Ms())
        .put("method.dropped.keys", droppedMethodKeys)
        .putTraceFields(traceContextReader.currentTraceFields())
        .build()
```

Only add `method.dropped.keys` when the dropped count is greater than zero.

The summary thread name should be:

```text
runtime-diagnostics-method-summary
```

- [x] **Step 7: Add `MethodDiagnosticsProbe`**

Create `MethodDiagnosticsProbe.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.method;

import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;

public class MethodDiagnosticsProbe implements Probe {

    private final MethodLatencyAggregator aggregator;

    public MethodDiagnosticsProbe(MethodLatencyAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public String name() {
        return "method";
    }

    @Override
    public void start(ProbeContext context) {
        new MethodSummaryReporter(aggregator, context.logger(), context.traceContextReader(), context.config().topN())
                .start(context.config().summaryInterval());
    }
}
```

- [x] **Step 8: Update method advice**

Rename `ProfilingAdvice` to `MethodTimingAdvice` and call:

```java
DiagnosticRuntime.recordMethod(className, methodName, descriptor, durationMs);
```

Keep `@Advice.OnMethodEnter(suppress = Throwable.class)` and `@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)`.

- [x] **Step 9: Run method probe tests**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=DiagnosticsMatcherTest,DiagnosticRuntimeTest,MethodKeyTest,MethodLatencyAggregatorTest,TokenBucketRateLimiterTest
```

Expected: PASS.

- [x] **Step 10: Commit method probe migration**

Run:

```bash
git add backend/runtime-diagnostics-agent
git commit -m "feat: migrate method diagnostics probe"
```

## Task 5: Add Exception Events

**Files:**
- Modify: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/DiagnosticRuntime.java`
- Modify: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/method/MethodTimingAdvice.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/exception/ExceptionDiagnosticsProbe.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/exception/ExceptionDiagnosticsProbeTest.java`
- Test: update forked JVM integration target later in Task 7

- [x] **Step 1: Write exception event test**

Create `ExceptionDiagnosticsProbeTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.exception;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionDiagnosticsProbeTest {

    @AfterEach
    void tearDown() {
        DiagnosticRuntime.resetForTests();
    }

    @Test
    void logsExceptionTypeWithoutMessageOrStackTrace() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiagnosticRuntime.initialize(config(), null, new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));

        DiagnosticRuntime.recordException(
                "com.example.SecretService",
                "fail",
                "()V",
                new IllegalStateException("password=secret")
        );

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.action\":\"exception_observed\"")
                .contains("\"event.outcome\":\"error\"")
                .contains("\"diagnostic.probe\":\"exception\"")
                .contains("\"exception.type\":\"java.lang.IllegalStateException\"")
                .contains("\"method.class\":\"com.example.SecretService\"")
                .contains("\"method.name\":\"fail\"")
                .doesNotContain("password=secret")
                .doesNotContain("stackTrace");
    }

    private static DiagnosticsConfig config() {
        return new DiagnosticsConfig(true, List.of("exception"), List.of("*"), List.of(), 1.0, 20,
                Duration.ofSeconds(60), 50, 10, 100, Duration.ofSeconds(60), Duration.ofSeconds(60));
    }
}
```

- [x] **Step 2: Run exception test and verify it fails**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=ExceptionDiagnosticsProbeTest
```

Expected: FAIL because `recordException` does not exist.

- [x] **Step 3: Add `recordException` to `DiagnosticRuntime`**

Add this public method:

```java
public static void recordException(String className, String methodName, String descriptor, Throwable throwable) {
    DiagnosticsConfig currentConfig = config;
    DiagnosticEventLogger currentLogger = logger;
    if (currentConfig == null || currentLogger == null || throwable == null || !currentConfig.probeEnabled("exception")) {
        return;
    }
    if (!sample(currentConfig.sampleRate())) {
        return;
    }
    MethodKey key = methodKey(className, methodName, descriptor, currentConfig.maxTrackedKeys());
    if (key == null) {
        return;
    }
    currentLogger.log(DiagnosticEvent.builder("exception_observed", "error", "exception")
            .put("exception.type", throwable.getClass().getName())
            .put("method.class", key.className())
            .put("method.name", key.methodName())
            .put("method.signature.hash", key.signatureHash())
            .putTraceFields(traceReader == null ? Map.of() : traceReader.currentTraceFields())
            .build());
}
```

Use the existing `methodKey` cache and `sample` helper. Do not log `throwable.getMessage()` or stack traces.

- [x] **Step 4: Add exception probe marker**

Create `ExceptionDiagnosticsProbe.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.exception;

import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;

public class ExceptionDiagnosticsProbe implements Probe {

    @Override
    public String name() {
        return "exception";
    }

    @Override
    public void start(ProbeContext context) {
        // Exception events are emitted by method advice when this probe is enabled.
    }
}
```

- [x] **Step 5: Capture thrown exceptions in method advice**

Update `MethodTimingAdvice.onExit` signature:

```java
@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
static void onExit(
        @Advice.Origin("#t") String className,
        @Advice.Origin("#m") String methodName,
        @Advice.Origin("#d") String descriptor,
        @Advice.Enter long startedAtNanos,
        @Advice.Thrown Throwable thrown
) {
    long durationMs = Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    DiagnosticRuntime.recordMethod(className, methodName, descriptor, durationMs);
    if (thrown != null) {
        DiagnosticRuntime.recordException(className, methodName, descriptor, thrown);
    }
}
```

- [x] **Step 6: Run exception tests**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=ExceptionDiagnosticsProbeTest,DiagnosticRuntimeTest
```

Expected: PASS.

- [x] **Step 7: Commit exception probe**

Run:

```bash
git add backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/DiagnosticRuntime.java backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/method/MethodTimingAdvice.java backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/exception backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/exception
git commit -m "feat: add exception diagnostics events"
```

## Task 6: Add Thread And JVM Probes

**Files:**
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/ScheduledProbeSupport.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/thread/ThreadDiagnosticsProbe.java`
- Create: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/jvm/JvmDiagnosticsProbe.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/thread/ThreadDiagnosticsProbeTest.java`
- Test: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/jvm/JvmDiagnosticsProbeTest.java`

- [x] **Step 1: Write thread probe test**

Create `ThreadDiagnosticsProbeTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.thread;

import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadDiagnosticsProbeTest {

    @Test
    void reportOnceLogsAggregatedThreadSnapshot() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ThreadDiagnosticsProbe probe = new ThreadDiagnosticsProbe(ManagementFactory.getThreadMXBean());

        probe.reportOnce(new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.action\":\"thread_snapshot\"")
                .contains("\"event.outcome\":\"snapshot\"")
                .contains("\"diagnostic.probe\":\"thread\"")
                .contains("\"thread.count\":")
                .contains("\"thread.state.runnable\":")
                .contains("\"thread.deadlock.count\":");
    }
}
```

- [x] **Step 2: Write JVM probe test**

Create `JvmDiagnosticsProbeTest.java`:

```java
package com.nowcoder.observability.runtimediagnostics.probes.jvm;

import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JvmDiagnosticsProbeTest {

    @Test
    void reportOnceLogsJvmRuntimeSummary() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JvmDiagnosticsProbe probe = new JvmDiagnosticsProbe(
                ManagementFactory.getRuntimeMXBean(),
                ManagementFactory.getMemoryMXBean(),
                ManagementFactory.getGarbageCollectorMXBeans(),
                ManagementFactory.getClassLoadingMXBean(),
                ManagementFactory.getThreadMXBean()
        );

        probe.reportOnce(new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.action\":\"jvm_runtime_summary\"")
                .contains("\"event.outcome\":\"success\"")
                .contains("\"diagnostic.probe\":\"jvm\"")
                .contains("\"jvm.uptime.ms\":")
                .contains("\"jvm.memory.heap.used.bytes\":")
                .contains("\"jvm.thread.count\":")
                .contains("\"jvm.class.loaded.count\":")
                .contains("\"jvm.gc.collection.count\":");
    }
}
```

- [x] **Step 3: Run new probe tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=ThreadDiagnosticsProbeTest,JvmDiagnosticsProbeTest
```

Expected: FAIL because thread and JVM probe classes do not exist.

- [x] **Step 4: Add scheduled probe support**

Create `ScheduledProbeSupport.java`:

```java
package com.nowcoder.observability.runtimediagnostics.core;

import java.time.Duration;

public final class ScheduledProbeSupport {

    private ScheduledProbeSupport() {
    }

    public static Thread startDaemon(String threadName, Duration interval, Runnable task) {
        long sleepMillis = Math.max(1_000, interval == null ? 60_000 : interval.toMillis());
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(sleepMillis);
                    task.run();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ignored) {
                }
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
```

- [x] **Step 5: Implement thread probe**

Create `ThreadDiagnosticsProbe.java` with a constructor accepting `ThreadMXBean`, a default constructor using `ManagementFactory.getThreadMXBean()`, and this `reportOnce` behavior:

```java
DiagnosticEvent event = DiagnosticEvent.builder("thread_snapshot", "snapshot", "thread")
        .put("thread.count", threadInfos.length)
        .put("thread.state.runnable", stateCounts.getOrDefault(Thread.State.RUNNABLE, 0))
        .put("thread.state.blocked", stateCounts.getOrDefault(Thread.State.BLOCKED, 0))
        .put("thread.state.waiting", stateCounts.getOrDefault(Thread.State.WAITING, 0))
        .put("thread.state.timed_waiting", stateCounts.getOrDefault(Thread.State.TIMED_WAITING, 0))
        .put("thread.deadlock.count", deadlocked == null ? 0 : deadlocked.length)
        .put("thread.lock.wait.count", lockWaitCount)
        .build();
logger.log(event);
```

`start(ProbeContext context)` should schedule `reportOnce(context.logger())` with:

```text
runtime-diagnostics-thread-snapshot
```

and `context.config().threadSnapshotInterval()`.

- [x] **Step 6: Implement JVM probe**

Create `JvmDiagnosticsProbe.java` with a default constructor using standard `ManagementFactory` beans and a test constructor accepting the beans. `reportOnce` should emit:

```java
DiagnosticEvent.builder("jvm_runtime_summary", "success", "jvm")
        .put("jvm.uptime.ms", runtimeMxBean.getUptime())
        .put("jvm.available.processors", Runtime.getRuntime().availableProcessors())
        .put("jvm.memory.heap.used.bytes", heap.getUsed())
        .put("jvm.memory.heap.max.bytes", heap.getMax())
        .put("jvm.memory.nonheap.used.bytes", nonHeap.getUsed())
        .put("jvm.thread.count", threadMxBean.getThreadCount())
        .put("jvm.class.loaded.count", classLoadingMxBean.getLoadedClassCount())
        .put("jvm.gc.collection.count", totalGcCount)
        .put("jvm.gc.collection.time.ms", totalGcTimeMs)
        .build()
```

`start(ProbeContext context)` should schedule `reportOnce(context.logger())` with:

```text
runtime-diagnostics-jvm-summary
```

and `context.config().jvmSummaryInterval()`.

- [x] **Step 7: Run thread and JVM tests**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent test -Dtest=ThreadDiagnosticsProbeTest,JvmDiagnosticsProbeTest
```

Expected: PASS.

- [x] **Step 8: Commit thread and JVM probes**

Run:

```bash
git add backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/core/ScheduledProbeSupport.java backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/thread backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/probes/jvm backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/thread backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/probes/jvm
git commit -m "feat: add thread and jvm diagnostics probes"
```

## Task 7: Wire Agent Startup And Integration Tests

**Files:**
- Modify: `backend/runtime-diagnostics-agent/src/main/java/com/nowcoder/observability/runtimediagnostics/RuntimeDiagnosticsAgent.java`
- Move: `backend/runtime-diagnostics-agent/src/test/java/com/example/methodprofiler/integration` to `backend/runtime-diagnostics-agent/src/test/java/com/example/runtimediagnostics/integration`
- Modify: `backend/runtime-diagnostics-agent/src/test/java/com/nowcoder/observability/runtimediagnostics/integration/RuntimeDiagnosticsAgentIT.java`

- [x] **Step 1: Update forked JVM integration target package**

Move integration sample classes into:

```text
backend/runtime-diagnostics-agent/src/test/java/com/example/runtimediagnostics/integration
```

Update their package declarations to:

```java
package com.example.runtimediagnostics.integration;
```

Keep the target service methods `slowWork()` and `throwingWork()`.

- [x] **Step 2: Update integration test**

Rename `MethodProfilerAgentIT` to `RuntimeDiagnosticsAgentIT` and use:

```java
package com.nowcoder.observability.runtimediagnostics.integration;

import com.example.runtimediagnostics.integration.AgentTargetMain;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDiagnosticsAgentIT {

    @Test
    void agentEmitsPhaseOneEventsWithoutChangingTargetExceptions() throws Exception {
        Path agentJar = Path.of("target", "runtime-diagnostics-agent-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertThat(agentJar).exists();
        String classpath = System.getProperty("java.class.path");
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-javaagent:" + agentJar + "=enabled=true,probes=method,exception,thread,jvm,includes=com.example.runtimediagnostics.integration.*,methodSlowThresholdMs=1,summaryInterval=1s,threadSnapshotInterval=1s,jvmSummaryInterval=1s,topN=5,maxEventsPerSecond=10");
        command.add("-cp");
        command.add(classpath);
        command.add(AgentTargetMain.class.getName());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean exited = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(exited).isTrue();
        assertThat(process.exitValue()).isEqualTo(0);
        assertThat(output)
                .contains("target exception propagated")
                .contains("\"event.category\":\"runtime_diagnostics\"")
                .contains("\"event.action\":\"method_slow_call\"")
                .contains("\"event.action\":\"method_latency_summary\"")
                .contains("\"event.action\":\"exception_observed\"")
                .contains("\"event.action\":\"thread_snapshot\"")
                .contains("\"event.action\":\"jvm_runtime_summary\"")
                .contains("\"method.class\":\"com.example.runtimediagnostics.integration.AgentTargetService\"")
                .doesNotContain("password=secret");
    }
}
```

- [x] **Step 3: Run integration test and verify it fails before startup wiring**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent -DskipTests package
mvn -q -pl :runtime-diagnostics-agent -Dtest=RuntimeDiagnosticsAgentIT verify
```

Expected: FAIL until `RuntimeDiagnosticsAgent` installs the transformer and starts probes.

- [x] **Step 4: Wire `RuntimeDiagnosticsAgent`**

Update `RuntimeDiagnosticsAgent.install` to:

```java
DiagnosticsConfig config = DiagnosticsConfigLoader.load(agentArgs);
if (!config.enabled() || instrumentation == null) {
    return;
}
DiagnosticsMatcher matcher = new DiagnosticsMatcher(config);
MethodLatencyAggregator methodAggregator = new MethodLatencyAggregator(config.maxTrackedKeys());
DiagnosticEventLogger logger = new DiagnosticEventLogger();
TraceContextReader traceReader = new TraceContextReader();
DiagnosticRuntime.initialize(config, methodAggregator, logger);
ProbeContext context = new ProbeContext(config, logger, traceReader);
new ProbeRegistry(List.of(
        new MethodDiagnosticsProbe(methodAggregator),
        new ExceptionDiagnosticsProbe(),
        new ThreadDiagnosticsProbe(),
        new JvmDiagnosticsProbe()
)).startEnabled(config, context);

new AgentBuilder.Default()
        .ignore(new ElementMatcher.Junction.AbstractBase<>() {
            @Override
            public boolean matches(TypeDescription target) {
                return !matcher.shouldInstrumentClass(target.getName());
            }
        })
        .type(new ElementMatcher.Junction.AbstractBase<>() {
            @Override
            public boolean matches(TypeDescription target) {
                return matcher.shouldInstrumentClass(target.getName());
            }
        })
        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                builder.visit(Advice.to(MethodTimingAdvice.class).on(not(isConstructor())
                        .and(not(isTypeInitializer()))
                        .and(not(isAbstract()))
                        .and(not(isNative()))
                        .and(not(isBridge()))
                        .and(not(isSynthetic())))))
        .installOn(instrumentation);
```

The startup failure message should use:

```text
[runtime-diagnostics-agent] disabled after startup failure:
```

- [x] **Step 5: Run integration test**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent -DskipTests package
mvn -q -pl :runtime-diagnostics-agent -Dtest=RuntimeDiagnosticsAgentIT verify
```

Expected: PASS.

- [x] **Step 6: Run full agent module tests**

Run:

```bash
cd backend
mvn -q -pl :runtime-diagnostics-agent verify
```

Expected: PASS.

- [x] **Step 7: Commit startup wiring**

Run:

```bash
git add backend/runtime-diagnostics-agent
git commit -m "feat: wire runtime diagnostics agent startup"
```

## Task 8: Update Deployment Wiring

**Files:**
- Modify: `deploy/Dockerfile.backend-service`
- Modify: `backend/scripts/run-backend-service.sh`
- Modify: `deploy/compose.runtime.services.single.yml`
- Modify: `deploy/compose.runtime.services.cluster.yml`
- Modify: `deploy/.env.single.example`
- Modify: `deploy/.env.cluster.example`
- Modify: `deploy/tests/observability_otel_default.sh`

- [x] **Step 1: Update deployment test expectations**

In `deploy/tests/observability_otel_default.sh`, replace method profiler assertions with runtime diagnostics assertions:

```bash
if ! rg -n 'RUNTIME_DIAGNOSTICS_ENABLED[=: ]+"?false"?|RUNTIME_DIAGNOSTICS_ENABLED=false' "${single_config}" >/dev/null; then
  echo "expected single config to keep runtime diagnostics disabled by default" >&2
  exit 1
fi

if ! rg -n 'RUNTIME_DIAGNOSTICS_ENABLED[=: ]+"?false"?|RUNTIME_DIAGNOSTICS_ENABLED=false' "${cluster_config}" >/dev/null; then
  echo "expected cluster config to keep runtime diagnostics disabled by default" >&2
  exit 1
fi

if ! rg -n 'RUNTIME_DIAGNOSTICS_INCLUDES[=: ]+"?com.nowcoder.community.\*"?|RUNTIME_DIAGNOSTICS_INCLUDES=com.nowcoder.community.\*' "${single_config}" >/dev/null; then
  echo "expected single config to use conservative community diagnostics includes" >&2
  exit 1
fi

if ! rg -n 'RUNTIME_DIAGNOSTICS_INCLUDES[=: ]+"?com.nowcoder.community.\*"?|RUNTIME_DIAGNOSTICS_INCLUDES=com.nowcoder.community.\*' "${cluster_config}" >/dev/null; then
  echo "expected cluster config to use conservative community diagnostics includes" >&2
  exit 1
fi

if rg -n 'METHOD_PROFILER_' "${single_config}" "${cluster_config}" >/dev/null; then
  echo "expected rendered configs to remove old method profiler settings" >&2
  exit 1
fi
```

- [x] **Step 2: Run deployment test and verify it fails**

Run:

```bash
./deploy/tests/observability_otel_default.sh
```

Expected: FAIL until compose files and env examples are updated.

- [x] **Step 3: Update Dockerfile build and copy**

In `deploy/Dockerfile.backend-service`, replace build module and jar copy references:

```sh
build_one() { mvn -q -DskipTests -pl ":${MODULE},:runtime-diagnostics-agent" -am package; };
```

Use:

```sh
diagnostics_agent_path="$(find ./runtime-diagnostics-agent/target -maxdepth 1 -type f -name "runtime-diagnostics-agent-*.jar" ! -name "original-*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" ! -name "*-tests.jar" -print | sort | head -n 1)";
```

Copy to:

```sh
cp "${diagnostics_agent_path}" /workspace/runtime-diagnostics-agent.jar
```

Final image copy:

```dockerfile
COPY --from=build /workspace/runtime-diagnostics-agent.jar /otel/runtime-diagnostics-agent.jar
```

- [x] **Step 4: Update startup script**

In `backend/scripts/run-backend-service.sh`, replace the operator note and old variable with:

```sh
# - RUNTIME_DIAGNOSTICS_ENABLED=true enables the optional runtime diagnostics Java agent.
runtime_diagnostics_enabled="${RUNTIME_DIAGNOSTICS_ENABLED:-false}"
```

Replace the agent block with:

```sh
if [ "${runtime_diagnostics_enabled}" = "true" ]; then
  if [ ! -f /otel/runtime-diagnostics-agent.jar ]; then
    echo "[backend-runtime] missing runtime diagnostics agent at /otel/runtime-diagnostics-agent.jar" >&2
    exit 1
  fi
  java_opts="${java_opts:+${java_opts} }-javaagent:/otel/runtime-diagnostics-agent.jar"
fi
```

- [x] **Step 5: Replace compose env names**

In `deploy/compose.runtime.services.single.yml` and `deploy/compose.runtime.services.cluster.yml`, replace each old block with:

```yaml
- RUNTIME_DIAGNOSTICS_ENABLED=${RUNTIME_DIAGNOSTICS_ENABLED:-false}
- RUNTIME_DIAGNOSTICS_PROBES=${RUNTIME_DIAGNOSTICS_PROBES:-method,exception,thread,jvm}
- RUNTIME_DIAGNOSTICS_INCLUDES=${RUNTIME_DIAGNOSTICS_INCLUDES:-com.nowcoder.community.*}
- RUNTIME_DIAGNOSTICS_EXCLUDES=${RUNTIME_DIAGNOSTICS_EXCLUDES:-}
- RUNTIME_DIAGNOSTICS_METHOD_SLOW_THRESHOLD_MS=${RUNTIME_DIAGNOSTICS_METHOD_SLOW_THRESHOLD_MS:-100}
- RUNTIME_DIAGNOSTICS_SUMMARY_INTERVAL=${RUNTIME_DIAGNOSTICS_SUMMARY_INTERVAL:-60s}
- RUNTIME_DIAGNOSTICS_THREAD_SNAPSHOT_INTERVAL=${RUNTIME_DIAGNOSTICS_THREAD_SNAPSHOT_INTERVAL:-60s}
- RUNTIME_DIAGNOSTICS_JVM_SUMMARY_INTERVAL=${RUNTIME_DIAGNOSTICS_JVM_SUMMARY_INTERVAL:-60s}
- RUNTIME_DIAGNOSTICS_TOP_N=${RUNTIME_DIAGNOSTICS_TOP_N:-50}
- RUNTIME_DIAGNOSTICS_SAMPLE_RATE=${RUNTIME_DIAGNOSTICS_SAMPLE_RATE:-1.0}
- RUNTIME_DIAGNOSTICS_MAX_EVENTS_PER_SECOND=${RUNTIME_DIAGNOSTICS_MAX_EVENTS_PER_SECOND:-20}
- RUNTIME_DIAGNOSTICS_MAX_TRACKED_KEYS=${RUNTIME_DIAGNOSTICS_MAX_TRACKED_KEYS:-10000}
```

- [x] **Step 6: Update env examples**

In both env example files, replace old method profiler variables with:

```dotenv
RUNTIME_DIAGNOSTICS_ENABLED=false
RUNTIME_DIAGNOSTICS_PROBES=method,exception,thread,jvm
RUNTIME_DIAGNOSTICS_INCLUDES=com.nowcoder.community.*
RUNTIME_DIAGNOSTICS_EXCLUDES=
RUNTIME_DIAGNOSTICS_METHOD_SLOW_THRESHOLD_MS=100
RUNTIME_DIAGNOSTICS_SUMMARY_INTERVAL=60s
RUNTIME_DIAGNOSTICS_THREAD_SNAPSHOT_INTERVAL=60s
RUNTIME_DIAGNOSTICS_JVM_SUMMARY_INTERVAL=60s
RUNTIME_DIAGNOSTICS_TOP_N=50
RUNTIME_DIAGNOSTICS_SAMPLE_RATE=1.0
RUNTIME_DIAGNOSTICS_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_MAX_TRACKED_KEYS=10000
```

- [x] **Step 7: Run deployment render test**

Run:

```bash
./deploy/tests/observability_otel_default.sh
```

Expected: PASS.

- [x] **Step 8: Verify old deployment names are removed from active deployment files**

Run:

```bash
rg -n 'METHOD_PROFILER|method-profiler-agent|method profiler' backend/runtime-diagnostics-agent backend/scripts deploy --glob '!deploy/README.md'
```

Expected: no output.

- [ ] **Step 9: Commit deployment wiring**

Run:

```bash
git add deploy/Dockerfile.backend-service backend/scripts/run-backend-service.sh deploy/compose.runtime.services.single.yml deploy/compose.runtime.services.cluster.yml deploy/.env.single.example deploy/.env.cluster.example deploy/tests/observability_otel_default.sh
git commit -m "chore: wire runtime diagnostics deployment"
```

## Task 9: Update Operations Documentation

**Files:**
- Modify: `deploy/README.md`
- Modify: `docs/handbook/operations.md`

- [x] **Step 1: Update deploy README runtime diagnostics section**

Replace the optional method profiler section in `deploy/README.md` with:

```markdown
### Optional Runtime Diagnostics Agent

Backend images include a generic JVM runtime diagnostics agent at `/otel/runtime-diagnostics-agent.jar`. It is disabled by default. Enable it for a short diagnostic run with:

```bash
RUNTIME_DIAGNOSTICS_ENABLED=true RUNTIME_DIAGNOSTICS_INCLUDES='com.nowcoder.community.*' ./deploy/deployment.sh up --topology single
```

The Phase 1 probes are `method`, `exception`, `thread`, and `jvm`. The agent emits `event.category=runtime_diagnostics` logs to the same stdout -> EDOT -> Elasticsearch path as other backend logs. It does not collect method arguments, return values, request bodies, SQL bind values, Redis keys or values, Kafka payloads, JWTs, cookies, or secrets.
```

- [x] **Step 2: Add operations guidance**

In `docs/handbook/operations.md`, add a runtime diagnostics section near observability operations:

```markdown
### Runtime Diagnostics Agent

`runtime-diagnostics-agent` is an optional JVM diagnostic agent for short troubleshooting sessions. It is disabled by default and is enabled per deployment with `RUNTIME_DIAGNOSTICS_ENABLED=true`.

Safe Phase 1 probes:

- `method`: method latency summaries and slow-call events.
- `exception`: exception type events from instrumented methods without raw messages or stack traces.
- `thread`: thread state snapshots, deadlock count, and lock-wait count.
- `jvm`: runtime, heap, non-heap, GC, class loading, and thread count summaries.

Useful Kibana filters:

```text
event.category : runtime_diagnostics
event.action : method_latency_summary
event.action : exception_observed
event.action : thread_snapshot
event.action : jvm_runtime_summary
trace.id : "<trace id>"
```

Keep includes narrow during diagnostic runs:

```text
RUNTIME_DIAGNOSTICS_INCLUDES=com.nowcoder.community.*
RUNTIME_DIAGNOSTICS_PROBES=method,exception,thread,jvm
```

The agent reads existing OTel/MDC trace context when present and does not create a new trace root. It must not be used to collect payload data or secrets.
```

- [x] **Step 3: Scan docs for old active names**

Run:

```bash
rg -n 'METHOD_PROFILER|/otel/method-profiler-agent.jar|Optional Method Profiler Agent' deploy/README.md docs/handbook/operations.md
```

Expected: no output.

- [x] **Step 4: Commit documentation**

Run:

```bash
git add deploy/README.md docs/handbook/operations.md
git commit -m "docs: document runtime diagnostics agent"
```

## Task 10: Final Verification

**Files:**
- Verify all files touched by Tasks 1-9.

- [ ] **Step 1: Run full agent verification**

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

- [ ] **Step 3: Check old names in active code and deployment surfaces**

Run:

```bash
rg -n 'METHOD_PROFILER|method-profiler-agent|com\.nowcoder\.observability\.methodprofiler' backend deploy docs/handbook --glob '!docs/superpowers/specs/2026-06-07-generic-method-profiler-agent-design.md' --glob '!docs/superpowers/plans/2026-06-08-generic-method-profiler-agent.md'
```

Expected: no output.

- [ ] **Step 4: Check new names exist**

Run:

```bash
rg -n 'runtime-diagnostics-agent|RUNTIME_DIAGNOSTICS_|runtime_diagnostics|com\.nowcoder\.observability\.runtimediagnostics' backend deploy docs/handbook
```

Expected: output includes the renamed module, deployment wiring, docs, and runtime diagnostics event category.

- [ ] **Step 5: Review git status**

Run:

```bash
git status --short
```

Expected: clean working tree after all task commits.
