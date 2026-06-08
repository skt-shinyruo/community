# Generic Method Profiler Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a generic, disabled-by-default JVM `-javaagent` that records bounded method latency summaries and slow-call events, then wire it into Community backend containers as an optional diagnostic tool.

**Architecture:** Add a standalone Maven agent module under `backend/method-profiler-agent` using Byte Buddy for method enter/exit timing. The agent has no Spring or Community dependency; it reads generic `METHOD_PROFILER_*` configuration, applies hard safety exclusions, aggregates method latency in memory, emits JSON-compatible structured events, and preserves existing OTel/MDC trace fields when available. Community integration only copies the agent into backend images and conditionally appends a second `-javaagent` in the runtime script.

**Tech Stack:** Java 17, Maven, Byte Buddy Java Agent, JUnit 5, AssertJ, SLF4J reflection/MDC reflection, Docker Compose, shell deploy smoke tests.

---

Source spec: `docs/superpowers/specs/2026-06-07-generic-method-profiler-agent-design.md`

## File Structure

- `backend/pom.xml`: add `method-profiler-agent` to the backend reactor and add managed plugin versions if the new module needs them.
- `backend/method-profiler-agent/pom.xml`: standalone agent module with Byte Buddy, shade plugin, jar manifest, and test dependencies.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/MethodProfilerAgent.java`: `premain` entry point; parses config, exits fast when disabled, installs Byte Buddy transformer, starts the summary scheduler.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/config/ProfilerConfig.java`: immutable runtime config object.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/config/ProfilerConfigLoader.java`: reads agent args, system properties, and environment variables.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/match/MethodProfilerMatcher.java`: class/method include/exclude and hard-exclude policy.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/model/MethodKey.java`: sanitized method identity and stable signature hash.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/model/MethodSnapshot.java`: immutable summary row.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/stats/LatencyHistogram.java`: bounded bucketed histogram for approximate p95.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/stats/MethodLatencyStats.java`: per-method count, total, max, and histogram.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/stats/MethodLatencyAggregator.java`: bounded map of method stats, top-N summary extraction, and overflow accounting.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/rate/TokenBucketRateLimiter.java`: slow-call rate limiter.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/trace/TraceContextReader.java`: reflection-based optional OTel/MDC trace lookup.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/log/ProfilerEventLogger.java`: JSON-compatible event output to SLF4J when available, stderr fallback otherwise.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/instrument/ProfilingAdvice.java`: Byte Buddy method advice hot path.
- `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/instrument/ProfilerRuntime.java`: static runtime holder used by advice.
- `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/**`: unit tests for config, matching, hashing, aggregation, rate limiting, logging, and trace reading.
- `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/integration/**`: forked JVM agent integration test helpers and Failsafe `*IT` tests.
- `deploy/Dockerfile.backend-service`: build and copy `/otel/method-profiler-agent.jar` into backend service images.
- `backend/scripts/run-backend-service.sh`: append `-javaagent:/otel/method-profiler-agent.jar` only when `METHOD_PROFILER_ENABLED=true`.
- `deploy/compose.runtime.services.single.yml`: pass generic method-profiler env vars to every backend deployable with conservative defaults.
- `deploy/compose.runtime.services.cluster.yml`: same as single topology.
- `deploy/.env.single.example`, `deploy/.env.cluster.example`: document generic `METHOD_PROFILER_*` settings and safe defaults.
- `deploy/tests/observability_otel_default.sh`: assert profiler env defaults render and observability/no-observability behavior remains safe.
- `docs/handbook/operations.md`, `deploy/README.md`: document safe diagnostic usage and Kibana queries.

## Task 1: Create Agent Module Skeleton

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/method-profiler-agent/pom.xml`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/MethodProfilerAgent.java`
- Create: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/MethodProfilerAgentSmokeTest.java`

- [ ] **Step 1: Add a failing smoke test for the agent class**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/MethodProfilerAgentSmokeTest.java`:

```java
package com.nowcoder.observability.methodprofiler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MethodProfilerAgentSmokeTest {

    @Test
    void exposesPremainEntryPoint() throws Exception {
        assertThat(MethodProfilerAgent.class.getMethod("premain", String.class, java.lang.instrument.Instrumentation.class))
                .isNotNull();
    }
}
```

- [ ] **Step 2: Register the Maven module**

In `backend/pom.xml`, add the module after `community-common`:

```xml
<module>method-profiler-agent</module>
```

- [ ] **Step 3: Create the agent module POM**

Create `backend/method-profiler-agent/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.nowcoder.community</groupId>
        <artifactId>community</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>method-profiler-agent</artifactId>
    <name>method-profiler-agent</name>
    <description>Generic JVM method latency profiler Java agent</description>
    <packaging>jar</packaging>

    <properties>
        <byte-buddy.version>1.14.18</byte-buddy.version>
        <community.repackage.skip>true</community.repackage.skip>
    </properties>

    <dependencies>
        <dependency>
            <groupId>net.bytebuddy</groupId>
            <artifactId>byte-buddy</artifactId>
            <version>${byte-buddy.version}</version>
        </dependency>
        <dependency>
            <groupId>net.bytebuddy</groupId>
            <artifactId>byte-buddy-agent</artifactId>
            <version>${byte-buddy.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <archive>
                        <manifestEntries>
                            <Premain-Class>com.nowcoder.observability.methodprofiler.MethodProfilerAgent</Premain-Class>
                            <Can-Redefine-Classes>false</Can-Redefine-Classes>
                            <Can-Retransform-Classes>false</Can-Retransform-Classes>
                        </manifestEntries>
                    </archive>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.3</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <manifestEntries>
                                        <Premain-Class>com.nowcoder.observability.methodprofiler.MethodProfilerAgent</Premain-Class>
                                        <Can-Redefine-Classes>false</Can-Redefine-Classes>
                                        <Can-Retransform-Classes>false</Can-Retransform-Classes>
                                    </manifestEntries>
                                </transformer>
                            </transformers>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <relocations>
                                <relocation>
                                    <pattern>net.bytebuddy</pattern>
                                    <shadedPattern>com.nowcoder.observability.methodprofiler.shaded.net.bytebuddy</shadedPattern>
                                </relocation>
                            </relocations>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>3.2.5</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Add the minimal premain class**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/MethodProfilerAgent.java`:

```java
package com.nowcoder.observability.methodprofiler;

import java.lang.instrument.Instrumentation;

public final class MethodProfilerAgent {

    private MethodProfilerAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        if (instrumentation == null) {
            return;
        }
    }
}
```

- [ ] **Step 5: Run the new module smoke test**

Run:

```bash
cd backend
mvn -q -pl :method-profiler-agent test -Dtest=MethodProfilerAgentSmokeTest
```

Expected: PASS. If Maven cannot resolve Byte Buddy due to network restrictions, rerun with approved network access.

- [ ] **Step 6: Verify the shaded jar manifest**

Run:

```bash
cd backend
mvn -q -pl :method-profiler-agent -DskipTests package
tmp_manifest="$(mktemp -d)"
cd "${tmp_manifest}"
jar xf /home/feng/code/project/community/backend/method-profiler-agent/target/method-profiler-agent-0.0.1-SNAPSHOT.jar META-INF/MANIFEST.MF
grep -F 'Premain-Class: com.nowcoder.observability.methodprofiler.MethodProfilerAgent' META-INF/MANIFEST.MF
cd /home/feng/code/project/community/backend
rm -rf "${tmp_manifest}"
```

Expected: `grep` prints the `Premain-Class` line and exits 0.

- [ ] **Step 7: Commit the skeleton**

```bash
git add backend/pom.xml backend/method-profiler-agent
git commit -m "feat: add method profiler agent module"
```

## Task 2: Configuration and Matching

**Files:**
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/config/ProfilerConfig.java`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/config/ProfilerConfigLoader.java`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/match/MethodProfilerMatcher.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/config/ProfilerConfigLoaderTest.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/match/MethodProfilerMatcherTest.java`

- [ ] **Step 1: Write config loader tests**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/config/ProfilerConfigLoaderTest.java`:

```java
package com.nowcoder.observability.methodprofiler.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilerConfigLoaderTest {

    @Test
    void defaultsAreDisabledAndBounded() {
        ProfilerConfig config = ProfilerConfigLoader.load("", Map.of(), Map.of());

        assertThat(config.enabled()).isFalse();
        assertThat(config.includes()).containsExactly("*");
        assertThat(config.excludes()).isEmpty();
        assertThat(config.slowThresholdMs()).isEqualTo(100);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.topN()).isEqualTo(50);
        assertThat(config.sampleRate()).isEqualTo(1.0);
        assertThat(config.maxEventsPerSecond()).isEqualTo(20);
        assertThat(config.maxTrackedMethods()).isEqualTo(10_000);
    }

    @Test
    void environmentOverridesDefaults() {
        ProfilerConfig config = ProfilerConfigLoader.load("", Map.of(), Map.of(
                "METHOD_PROFILER_ENABLED", "true",
                "METHOD_PROFILER_INCLUDES", "com.example.*,org.demo.Service",
                "METHOD_PROFILER_EXCLUDES", "com.example.internal.*",
                "METHOD_PROFILER_SLOW_THRESHOLD_MS", "250",
                "METHOD_PROFILER_SUMMARY_INTERVAL", "30s",
                "METHOD_PROFILER_TOP_N", "25",
                "METHOD_PROFILER_SAMPLE_RATE", "0.25",
                "METHOD_PROFILER_MAX_EVENTS_PER_SECOND", "7",
                "METHOD_PROFILER_MAX_TRACKED_METHODS", "99"
        ));

        assertThat(config.enabled()).isTrue();
        assertThat(config.includes()).containsExactly("com.example.*", "org.demo.Service");
        assertThat(config.excludes()).containsExactly("com.example.internal.*");
        assertThat(config.slowThresholdMs()).isEqualTo(250);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.topN()).isEqualTo(25);
        assertThat(config.sampleRate()).isEqualTo(0.25);
        assertThat(config.maxEventsPerSecond()).isEqualTo(7);
        assertThat(config.maxTrackedMethods()).isEqualTo(99);
    }

    @Test
    void agentArgsOverrideEnvironment() {
        ProfilerConfig config = ProfilerConfigLoader.load(
                "enabled=true,includes=com.agent.*,slowThresholdMs=333,summaryInterval=5s,topN=3,sampleRate=0.5,maxEventsPerSecond=2,maxTrackedMethods=10",
                Map.of(),
                Map.of("METHOD_PROFILER_ENABLED", "false", "METHOD_PROFILER_INCLUDES", "com.env.*")
        );

        assertThat(config.enabled()).isTrue();
        assertThat(config.includes()).containsExactly("com.agent.*");
        assertThat(config.slowThresholdMs()).isEqualTo(333);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.topN()).isEqualTo(3);
        assertThat(config.sampleRate()).isEqualTo(0.5);
        assertThat(config.maxEventsPerSecond()).isEqualTo(2);
        assertThat(config.maxTrackedMethods()).isEqualTo(10);
    }
}
```

- [ ] **Step 2: Write matcher tests**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/match/MethodProfilerMatcherTest.java`:

```java
package com.nowcoder.observability.methodprofiler.match;

import com.nowcoder.observability.methodprofiler.config.ProfilerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MethodProfilerMatcherTest {

    @Test
    void includeStarMeansEligibleAfterHardExcludes() {
        MethodProfilerMatcher matcher = new MethodProfilerMatcher(config(List.of("*"), List.of()));

        assertThat(matcher.shouldInstrumentClass("com.example.Service")).isTrue();
        assertThat(matcher.shouldInstrumentClass("java.lang.String")).isFalse();
        assertThat(matcher.shouldInstrumentClass("org.slf4j.Logger")).isFalse();
        assertThat(matcher.shouldInstrumentClass("ch.qos.logback.classic.Logger")).isFalse();
        assertThat(matcher.shouldInstrumentClass("net.bytebuddy.agent.builder.AgentBuilder")).isFalse();
        assertThat(matcher.shouldInstrumentClass("com.nowcoder.observability.methodprofiler.MethodProfilerAgent")).isFalse();
    }

    @Test
    void userExcludesCannotRemoveHardExcludes() {
        MethodProfilerMatcher matcher = new MethodProfilerMatcher(config(List.of("java.*", "com.example.*"), List.of()));

        assertThat(matcher.shouldInstrumentClass("java.util.ArrayList")).isFalse();
        assertThat(matcher.shouldInstrumentClass("com.example.Service")).isTrue();
    }

    @Test
    void userExcludesBlockIncludedClasses() {
        MethodProfilerMatcher matcher = new MethodProfilerMatcher(config(
                List.of("com.example.*"),
                List.of("com.example.internal.*")
        ));

        assertThat(matcher.shouldInstrumentClass("com.example.Service")).isTrue();
        assertThat(matcher.shouldInstrumentClass("com.example.internal.SecretService")).isFalse();
    }

    @Test
    void skipsUnsafeMethods() throws NoSuchMethodException {
        MethodProfilerMatcher matcher = new MethodProfilerMatcher(config(List.of("com.example.*"), List.of()));

        assertThat(matcher.shouldInstrumentMethod(Sample.class.getDeclaredMethod("normal"))).isTrue();
        assertThat(matcher.shouldInstrumentMethod(Sample.class.getDeclaredMethod("nativeMethod"))).isFalse();
        assertThat(matcher.shouldInstrumentMethod(AbstractSample.class.getDeclaredMethod("abstractMethod"))).isFalse();
    }

    private static ProfilerConfig config(List<String> includes, List<String> excludes) {
        return new ProfilerConfig(false, includes, excludes, 100, Duration.ofSeconds(60), 50, 1.0, 20, 10_000);
    }

    static class Sample {
        void normal() {
        }

        native void nativeMethod();
    }

    abstract static class AbstractSample {
        abstract void abstractMethod();
    }
}
```

- [ ] **Step 3: Implement `ProfilerConfig`**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/config/ProfilerConfig.java`:

```java
package com.nowcoder.observability.methodprofiler.config;

import java.time.Duration;
import java.util.List;

public record ProfilerConfig(
        boolean enabled,
        List<String> includes,
        List<String> excludes,
        long slowThresholdMs,
        Duration summaryInterval,
        int topN,
        double sampleRate,
        int maxEventsPerSecond,
        int maxTrackedMethods
) {

    public ProfilerConfig {
        includes = List.copyOf(includes == null || includes.isEmpty() ? List.of("*") : includes);
        excludes = List.copyOf(excludes == null ? List.of() : excludes);
        slowThresholdMs = Math.max(0, slowThresholdMs);
        summaryInterval = summaryInterval == null || summaryInterval.isNegative() || summaryInterval.isZero()
                ? Duration.ofSeconds(60)
                : summaryInterval;
        topN = Math.max(1, topN);
        sampleRate = Math.max(0.0, Math.min(1.0, sampleRate));
        maxEventsPerSecond = Math.max(0, maxEventsPerSecond);
        maxTrackedMethods = Math.max(1, maxTrackedMethods);
    }
}
```

- [ ] **Step 4: Implement `ProfilerConfigLoader`**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/config/ProfilerConfigLoader.java`:

```java
package com.nowcoder.observability.methodprofiler.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class ProfilerConfigLoader {

    private ProfilerConfigLoader() {
    }

    public static ProfilerConfig load(String agentArgs) {
        return load(agentArgs, propertiesMap(System.getProperties()), System.getenv());
    }

    static ProfilerConfig load(String agentArgs, Map<String, String> systemProperties, Map<String, String> environment) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("enabled", first(systemProperties.get("method.profiler.enabled"), environment.get("METHOD_PROFILER_ENABLED"), "false"));
        values.put("includes", first(systemProperties.get("method.profiler.includes"), environment.get("METHOD_PROFILER_INCLUDES"), "*"));
        values.put("excludes", first(systemProperties.get("method.profiler.excludes"), environment.get("METHOD_PROFILER_EXCLUDES"), ""));
        values.put("slowThresholdMs", first(systemProperties.get("method.profiler.slowThresholdMs"), environment.get("METHOD_PROFILER_SLOW_THRESHOLD_MS"), "100"));
        values.put("summaryInterval", first(systemProperties.get("method.profiler.summaryInterval"), environment.get("METHOD_PROFILER_SUMMARY_INTERVAL"), "60s"));
        values.put("topN", first(systemProperties.get("method.profiler.topN"), environment.get("METHOD_PROFILER_TOP_N"), "50"));
        values.put("sampleRate", first(systemProperties.get("method.profiler.sampleRate"), environment.get("METHOD_PROFILER_SAMPLE_RATE"), "1.0"));
        values.put("maxEventsPerSecond", first(systemProperties.get("method.profiler.maxEventsPerSecond"), environment.get("METHOD_PROFILER_MAX_EVENTS_PER_SECOND"), "20"));
        values.put("maxTrackedMethods", first(systemProperties.get("method.profiler.maxTrackedMethods"), environment.get("METHOD_PROFILER_MAX_TRACKED_METHODS"), "10000"));
        parseAgentArgs(agentArgs).forEach(values::put);

        return new ProfilerConfig(
                Boolean.parseBoolean(values.get("enabled")),
                csv(values.get("includes")),
                csv(values.get("excludes")),
                parseLong(values.get("slowThresholdMs"), 100),
                parseDuration(values.get("summaryInterval"), Duration.ofSeconds(60)),
                parseInt(values.get("topN"), 50),
                parseDouble(values.get("sampleRate"), 1.0),
                parseInt(values.get("maxEventsPerSecond"), 20),
                parseInt(values.get("maxTrackedMethods"), 10_000)
        );
    }

    private static Map<String, String> parseAgentArgs(String agentArgs) {
        Map<String, String> result = new LinkedHashMap<>();
        if (agentArgs == null || agentArgs.isBlank()) {
            return result;
        }
        for (String pair : agentArgs.split(",")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = pair.substring(0, index).trim();
            String value = pair.substring(index + 1).trim();
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
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

    private static String first(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
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

- [ ] **Step 5: Implement `MethodProfilerMatcher`**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/match/MethodProfilerMatcher.java`:

```java
package com.nowcoder.observability.methodprofiler.match;

import com.nowcoder.observability.methodprofiler.config.ProfilerConfig;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public class MethodProfilerMatcher {

    private static final List<String> HARD_EXCLUDES = List.of(
            "java.*",
            "javax.*",
            "jakarta.*",
            "sun.*",
            "jdk.*",
            "org.slf4j.*",
            "ch.qos.logback.*",
            "net.bytebuddy.*",
            "com.nowcoder.observability.methodprofiler.*"
    );

    private final ProfilerConfig config;

    public MethodProfilerMatcher(ProfilerConfig config) {
        this.config = config;
    }

    public boolean shouldInstrumentClass(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        if (matchesAny(HARD_EXCLUDES, className) || matchesAny(config.excludes(), className)) {
            return false;
        }
        return matchesAny(config.includes(), className);
    }

    public boolean shouldInstrumentMethod(Method method) {
        if (method == null) {
            return false;
        }
        int modifiers = method.getModifiers();
        return !Modifier.isAbstract(modifiers)
                && !Modifier.isNative(modifiers)
                && !method.isBridge()
                && !method.isSynthetic();
    }

    private boolean matchesAny(List<String> patterns, String className) {
        for (String pattern : patterns) {
            if (matches(pattern, className)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String pattern, String className) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        String trimmed = pattern.trim();
        if ("*".equals(trimmed)) {
            return true;
        }
        if (trimmed.endsWith(".*")) {
            String prefix = trimmed.substring(0, trimmed.length() - 1);
            return className.startsWith(prefix);
        }
        return className.equals(trimmed);
    }
}
```

- [ ] **Step 6: Run config and matcher tests**

Run:

```bash
cd backend
mvn -q -pl :method-profiler-agent test -Dtest=ProfilerConfigLoaderTest,MethodProfilerMatcherTest
```

Expected: PASS.

- [ ] **Step 7: Commit configuration and matching**

```bash
git add backend/method-profiler-agent
git commit -m "feat: add method profiler configuration"
```

## Task 3: Method Identity, Aggregation, and Rate Limiting

**Files:**
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/model/MethodKey.java`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/model/MethodSnapshot.java`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/stats/LatencyHistogram.java`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/stats/MethodLatencyStats.java`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/stats/MethodLatencyAggregator.java`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/rate/TokenBucketRateLimiter.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/model/MethodKeyTest.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/stats/MethodLatencyAggregatorTest.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/rate/TokenBucketRateLimiterTest.java`

- [ ] **Step 1: Write method-key tests**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/model/MethodKeyTest.java`:

```java
package com.nowcoder.observability.methodprofiler.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MethodKeyTest {

    @Test
    void stableHashUsesClassMethodParametersAndReturnType() throws Exception {
        MethodKey firstKey = MethodKey.from(Sample.class, "work", String.class, new Class<?>[]{String.class});
        MethodKey sameFirstKey = MethodKey.from(Sample.class, "work", String.class, new Class<?>[]{String.class});
        MethodKey secondKey = MethodKey.from(Sample.class, "work", String.class, new Class<?>[]{Integer.class});

        assertThat(firstKey.className()).isEqualTo("com.nowcoder.observability.methodprofiler.model.MethodKeyTest$Sample");
        assertThat(firstKey.methodName()).isEqualTo("work");
        assertThat(firstKey.signatureHash()).isEqualTo(sameFirstKey.signatureHash());
        assertThat(firstKey.signatureHash()).isNotEqualTo(secondKey.signatureHash());
        assertThat(firstKey.signatureHash()).hasSize(16);
    }

    @Test
    void stableHashCanUseByteBuddyOriginStrings() {
        MethodKey firstKey = MethodKey.from("com.example.Service", "work", "(Ljava/lang/String;)Ljava/lang/String;");
        MethodKey sameFirstKey = MethodKey.from("com.example.Service", "work", "(Ljava/lang/String;)Ljava/lang/String;");
        MethodKey secondKey = MethodKey.from("com.example.Service", "work", "(Ljava/lang/Integer;)Ljava/lang/String;");

        assertThat(firstKey.signatureHash()).isEqualTo(sameFirstKey.signatureHash());
        assertThat(firstKey.signatureHash()).isNotEqualTo(secondKey.signatureHash());
    }

    static class Sample {
        String work(String value) {
            return value;
        }

        String work(Integer value) {
            return String.valueOf(value);
        }
    }
}
```

- [ ] **Step 2: Write aggregation tests**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/stats/MethodLatencyAggregatorTest.java`:

```java
package com.nowcoder.observability.methodprofiler.stats;

import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.model.MethodSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MethodLatencyAggregatorTest {

    @Test
    void summarizesTopMethodsByMaxDurationDescending() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        MethodKey first = new MethodKey("com.example.First", "run", "0000000000000001");
        MethodKey second = new MethodKey("com.example.Second", "run", "0000000000000002");

        aggregator.record(first, 10);
        aggregator.record(first, 30);
        aggregator.record(second, 100);

        List<MethodSnapshot> snapshots = aggregator.topSnapshots(2);

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).key()).isEqualTo(second);
        assertThat(snapshots.get(0).maxMs()).isEqualTo(100);
        assertThat(snapshots.get(1).key()).isEqualTo(first);
        assertThat(snapshots.get(1).count()).isEqualTo(2);
        assertThat(snapshots.get(1).avgMs()).isEqualTo(20);
    }

    @Test
    void capsTrackedMethodKeys() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(1);

        aggregator.record(new MethodKey("com.example.First", "run", "0000000000000001"), 10);
        aggregator.record(new MethodKey("com.example.Second", "run", "0000000000000002"), 20);

        assertThat(aggregator.topSnapshots(10)).hasSize(1);
        assertThat(aggregator.droppedMethodKeys()).isEqualTo(1);
    }
}
```

- [ ] **Step 3: Write rate limiter tests**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/rate/TokenBucketRateLimiterTest.java`:

```java
package com.nowcoder.observability.methodprofiler.rate;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    @Test
    void allowsConfiguredEventsPerSecondAndRefills() {
        AtomicLong now = new AtomicLong(0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, now::get);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        now.set(1_000_000_000L);
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void zeroLimitDisablesEvents() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(0, System::nanoTime);

        assertThat(limiter.tryAcquire()).isFalse();
    }
}
```

- [ ] **Step 4: Implement `MethodKey`**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/model/MethodKey.java`:

```java
package com.nowcoder.observability.methodprofiler.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;

public record MethodKey(String className, String methodName, String signatureHash) {

    public static MethodKey from(String className, String methodName, String descriptor) {
        String safeClassName = sanitize(className);
        String safeMethodName = sanitize(methodName);
        String rawSignature = safeClassName + "#" + safeMethodName + ":" + sanitize(descriptor);
        return new MethodKey(safeClassName, safeMethodName, hash(rawSignature));
    }

    public static MethodKey from(Class<?> declaringClass, String methodName, Class<?> returnType, Class<?>[] parameterTypes) {
        String className = sanitize(declaringClass == null ? null : declaringClass.getName());
        String safeMethodName = sanitize(methodName);
        String parameters = parameterTypes == null ? "" : Stream.of(parameterTypes)
                .map(Class::getName)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String rawSignature = className + "#" + safeMethodName + "(" + parameters + "):" + (returnType == null ? "void" : returnType.getName());
        return new MethodKey(className, safeMethodName, hash(rawSignature));
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private static String hash(String rawSignature) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawSignature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toUnsignedString(rawSignature.hashCode(), 16);
        }
    }
}
```

- [ ] **Step 5: Implement snapshots and histograms**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/model/MethodSnapshot.java`:

```java
package com.nowcoder.observability.methodprofiler.model;

public record MethodSnapshot(
        MethodKey key,
        long count,
        long avgMs,
        long maxMs,
        long p95Ms
) {
}
```

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/stats/LatencyHistogram.java`:

```java
package com.nowcoder.observability.methodprofiler.stats;

import java.util.concurrent.atomic.AtomicLongArray;

class LatencyHistogram {

    private static final long[] UPPER_BOUNDS_MS = {
            1, 2, 5, 10, 20, 50, 100, 200, 500,
            1_000, 2_000, 5_000, 10_000, 30_000, 60_000, Long.MAX_VALUE
    };

    private final AtomicLongArray buckets = new AtomicLongArray(UPPER_BOUNDS_MS.length);

    void record(long durationMs) {
        long safeDuration = Math.max(0, durationMs);
        for (int i = 0; i < UPPER_BOUNDS_MS.length; i++) {
            if (safeDuration <= UPPER_BOUNDS_MS[i]) {
                buckets.incrementAndGet(i);
                return;
            }
        }
    }

    long percentile95(long totalCount) {
        if (totalCount <= 0) {
            return 0;
        }
        long target = Math.max(1, (long) Math.ceil(totalCount * 0.95));
        long seen = 0;
        for (int i = 0; i < UPPER_BOUNDS_MS.length; i++) {
            seen += buckets.get(i);
            if (seen >= target) {
                return UPPER_BOUNDS_MS[i] == Long.MAX_VALUE ? 60_000 : UPPER_BOUNDS_MS[i];
            }
        }
        return 0;
    }
}
```

- [ ] **Step 6: Implement stats and aggregator**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/stats/MethodLatencyStats.java`:

```java
package com.nowcoder.observability.methodprofiler.stats;

import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.model.MethodSnapshot;

import java.util.concurrent.atomic.AtomicLong;

class MethodLatencyStats {

    private final MethodKey key;
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong totalMs = new AtomicLong();
    private final AtomicLong maxMs = new AtomicLong();
    private final LatencyHistogram histogram = new LatencyHistogram();

    MethodLatencyStats(MethodKey key) {
        this.key = key;
    }

    void record(long durationMs) {
        long safeDuration = Math.max(0, durationMs);
        count.incrementAndGet();
        totalMs.addAndGet(safeDuration);
        histogram.record(safeDuration);
        maxMs.accumulateAndGet(safeDuration, Math::max);
    }

    MethodSnapshot snapshot() {
        long c = count.get();
        long total = totalMs.get();
        return new MethodSnapshot(key, c, c == 0 ? 0 : total / c, maxMs.get(), histogram.percentile95(c));
    }
}
```

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/stats/MethodLatencyAggregator.java`:

```java
package com.nowcoder.observability.methodprofiler.stats;

import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.model.MethodSnapshot;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MethodLatencyAggregator {

    private final int maxTrackedMethods;
    private final ConcurrentHashMap<MethodKey, MethodLatencyStats> stats = new ConcurrentHashMap<>();
    private final AtomicLong droppedMethodKeys = new AtomicLong();

    public MethodLatencyAggregator(int maxTrackedMethods) {
        this.maxTrackedMethods = Math.max(1, maxTrackedMethods);
    }

    public void record(MethodKey key, long durationMs) {
        MethodLatencyStats existing = stats.get(key);
        if (existing != null) {
            existing.record(durationMs);
            return;
        }
        if (stats.size() >= maxTrackedMethods) {
            droppedMethodKeys.incrementAndGet();
            return;
        }
        MethodLatencyStats created = new MethodLatencyStats(key);
        MethodLatencyStats raced = stats.putIfAbsent(key, created);
        (raced == null ? created : raced).record(durationMs);
    }

    public List<MethodSnapshot> topSnapshots(int topN) {
        int limit = Math.max(1, topN);
        return stats.values().stream()
                .map(MethodLatencyStats::snapshot)
                .sorted(Comparator.comparingLong(MethodSnapshot::maxMs).reversed()
                        .thenComparing(snapshot -> snapshot.key().signatureHash()))
                .limit(limit)
                .toList();
    }

    public long droppedMethodKeys() {
        return droppedMethodKeys.get();
    }
}
```

- [ ] **Step 7: Implement `TokenBucketRateLimiter`**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/rate/TokenBucketRateLimiter.java`:

```java
package com.nowcoder.observability.methodprofiler.rate;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class TokenBucketRateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int maxEventsPerSecond;
    private final LongSupplier nanoTime;
    private final AtomicInteger tokens;
    private final AtomicLong lastRefillNanos;

    public TokenBucketRateLimiter(int maxEventsPerSecond, LongSupplier nanoTime) {
        this.maxEventsPerSecond = Math.max(0, maxEventsPerSecond);
        this.nanoTime = nanoTime;
        this.tokens = new AtomicInteger(this.maxEventsPerSecond);
        this.lastRefillNanos = new AtomicLong(nanoTime.getAsLong());
    }

    public boolean tryAcquire() {
        if (maxEventsPerSecond <= 0) {
            return false;
        }
        refill();
        while (true) {
            int current = tokens.get();
            if (current <= 0) {
                return false;
            }
            if (tokens.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    private void refill() {
        long now = nanoTime.getAsLong();
        long last = lastRefillNanos.get();
        if (now - last < NANOS_PER_SECOND) {
            return;
        }
        if (lastRefillNanos.compareAndSet(last, now)) {
            tokens.set(maxEventsPerSecond);
        }
    }
}
```

- [ ] **Step 8: Run model, stats, and rate tests**

Run:

```bash
cd backend
mvn -q -pl :method-profiler-agent test -Dtest=MethodKeyTest,MethodLatencyAggregatorTest,TokenBucketRateLimiterTest
```

Expected: PASS.

- [ ] **Step 9: Commit aggregation support**

```bash
git add backend/method-profiler-agent
git commit -m "feat: add method profiler aggregation"
```

## Task 4: Logging and Trace Correlation

**Files:**
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/trace/TraceContextReader.java`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/log/ProfilerEventLogger.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/trace/TraceContextReaderTest.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/log/ProfilerEventLoggerTest.java`

- [ ] **Step 1: Write trace reader tests**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/trace/TraceContextReaderTest.java`:

```java
package com.nowcoder.observability.methodprofiler.trace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextReaderTest {

    @Test
    void returnsEmptyWhenNoOptionalTraceLibrariesAreAvailableOrActive() {
        TraceContextReader reader = new TraceContextReader();

        Map<String, String> fields = reader.currentTraceFields();

        assertThat(fields).doesNotContainKeys("trace.id", "span.id");
    }
}
```

- [ ] **Step 2: Write event logger tests**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/log/ProfilerEventLoggerTest.java`:

```java
package com.nowcoder.observability.methodprofiler.log;

import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.model.MethodSnapshot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilerEventLoggerTest {

    @Test
    void writesSummaryJsonWithoutArgumentsOrReturnValues() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProfilerEventLogger logger = new ProfilerEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8));
        MethodSnapshot snapshot = new MethodSnapshot(
                new MethodKey("com.example.SecretService", "findPassword", "abcdef1234567890"),
                3,
                10,
                30,
                20
        );

        logger.logSummary(List.of(snapshot), 0, Map.of("trace.id", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.category\":\"method\"")
                .contains("\"event.action\":\"method_latency_summary\"")
                .contains("\"method.class\":\"com.example.SecretService\"")
                .contains("\"method.name\":\"findPassword\"")
                .contains("\"trace.id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"")
                .contains("\"duration.max.ms\":30")
                .doesNotContain("argument")
                .doesNotContain("return.value");
    }

    @Test
    void writesSlowCallJson() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProfilerEventLogger logger = new ProfilerEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8));

        logger.logSlowCall(new MethodKey("com.example.Service", "work", "abcdef1234567890"), 123, 100, Map.of());

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"event.action\":\"method_slow_call\"")
                .contains("\"duration.ms\":123")
                .contains("\"threshold.ms\":100");
    }
}
```

- [ ] **Step 3: Implement `TraceContextReader`**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/trace/TraceContextReader.java`:

```java
package com.nowcoder.observability.methodprofiler.trace;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public class TraceContextReader {

    public Map<String, String> currentTraceFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        readOtel(fields);
        if (!fields.containsKey("trace.id")) {
            readMdc(fields);
        }
        return fields;
    }

    private void readOtel(Map<String, String> fields) {
        try {
            Class<?> spanClass = Class.forName("io.opentelemetry.api.trace.Span");
            Object span = spanClass.getMethod("current").invoke(null);
            Object spanContext = spanClass.getMethod("getSpanContext").invoke(span);
            Class<?> spanContextClass = Class.forName("io.opentelemetry.api.trace.SpanContext");
            boolean valid = (Boolean) spanContextClass.getMethod("isValid").invoke(spanContext);
            if (!valid) {
                return;
            }
            fields.put("trace.id", String.valueOf(spanContextClass.getMethod("getTraceId").invoke(spanContext)));
            fields.put("span.id", String.valueOf(spanContextClass.getMethod("getSpanId").invoke(spanContext)));
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private void readMdc(Map<String, String> fields) {
        try {
            Class<?> mdcClass = Class.forName("org.slf4j.MDC");
            Method get = mdcClass.getMethod("get", String.class);
            Object traceId = get.invoke(null, "trace.id");
            Object spanId = get.invoke(null, "span.id");
            if (traceId instanceof String value && !value.isBlank()) {
                fields.put("trace.id", value);
            }
            if (spanId instanceof String value && !value.isBlank()) {
                fields.put("span.id", value);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }
}
```

- [ ] **Step 4: Implement `ProfilerEventLogger`**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/log/ProfilerEventLogger.java`:

```java
package com.nowcoder.observability.methodprofiler.log;

import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.model.MethodSnapshot;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfilerEventLogger {

    private final PrintStream fallback;
    private final Object slf4jLogger;
    private final Method slf4jInfo;

    public ProfilerEventLogger() {
        this(System.err, Slf4jBinding.tryCreate());
    }

    public ProfilerEventLogger(PrintStream fallback) {
        this(fallback, null);
    }

    private ProfilerEventLogger(PrintStream fallback, Slf4jBinding binding) {
        this.fallback = fallback;
        this.slf4jLogger = binding == null ? null : binding.logger();
        this.slf4jInfo = binding == null ? null : binding.infoMethod();
    }

    public void logSummary(List<MethodSnapshot> snapshots, long droppedMethodKeys, Map<String, String> traceFields) {
        for (MethodSnapshot snapshot : snapshots) {
            Map<String, Object> fields = base("method_latency_summary", "success", traceFields);
            addMethod(fields, snapshot.key());
            fields.put("method.invocation.count", snapshot.count());
            fields.put("duration.avg.ms", snapshot.avgMs());
            fields.put("duration.max.ms", snapshot.maxMs());
            fields.put("duration.p95.ms", snapshot.p95Ms());
            if (droppedMethodKeys > 0) {
                fields.put("method.dropped.keys", droppedMethodKeys);
            }
            write(fields);
        }
    }

    public void logSlowCall(MethodKey key, long durationMs, long thresholdMs, Map<String, String> traceFields) {
        Map<String, Object> fields = base("method_slow_call", "threshold", traceFields);
        addMethod(fields, key);
        fields.put("duration.ms", durationMs);
        fields.put("threshold.ms", thresholdMs);
        write(fields);
    }

    private Map<String, Object> base(String action, String outcome, Map<String, String> traceFields) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("@timestamp", Instant.now().toString());
        fields.put("event.category", "method");
        fields.put("event.action", action);
        fields.put("event.outcome", outcome);
        if (traceFields != null) {
            traceFields.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    fields.put(key, value);
                }
            });
        }
        return fields;
    }

    private void addMethod(Map<String, Object> fields, MethodKey key) {
        fields.put("method.class", key.className());
        fields.put("method.name", key.methodName());
        fields.put("method.signature.hash", key.signatureHash());
    }

    private void write(Map<String, Object> fields) {
        try {
            String json = toJson(fields);
            if (slf4jLogger != null && slf4jInfo != null) {
                slf4jInfo.invoke(slf4jLogger, json);
            } else {
                fallback.println(json);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
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
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Slf4jBinding(Object logger, Method infoMethod) {

        static Slf4jBinding tryCreate() {
            try {
                Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
                Object logger = loggerFactoryClass.getMethod("getLogger", String.class)
                        .invoke(null, "method-profiler");
                Class<?> loggerClass = Class.forName("org.slf4j.Logger");
                Method info = loggerClass.getMethod("info", String.class);
                return new Slf4jBinding(logger, info);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }
}
```

- [ ] **Step 5: Run logging and trace tests**

Run:

```bash
cd backend
mvn -q -pl :method-profiler-agent test -Dtest=TraceContextReaderTest,ProfilerEventLoggerTest
```

Expected: PASS.

- [ ] **Step 6: Commit logging and trace support**

```bash
git add backend/method-profiler-agent
git commit -m "feat: add method profiler event logging"
```

## Task 5: Byte Buddy Instrumentation Runtime

**Files:**
- Modify: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/MethodProfilerAgent.java`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/instrument/ProfilerRuntime.java`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/instrument/ProfilingAdvice.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/instrument/ProfilerRuntimeTest.java`

- [ ] **Step 1: Write runtime tests**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/instrument/ProfilerRuntimeTest.java`:

```java
package com.nowcoder.observability.methodprofiler.instrument;

import com.nowcoder.observability.methodprofiler.config.ProfilerConfig;
import com.nowcoder.observability.methodprofiler.stats.MethodLatencyAggregator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilerRuntimeTest {

    @AfterEach
    void tearDown() {
        ProfilerRuntime.resetForTests();
    }

    @Test
    void recordsMethodDurationWhenConfigured() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        ProfilerRuntime.initialize(config(), aggregator);

        ProfilerRuntime.record("com.example.Service", "work", "void work()", 125);

        assertThat(aggregator.topSnapshots(1))
                .singleElement()
                .extracting(snapshot -> snapshot.key().className(), snapshot -> snapshot.key().methodName(), snapshot -> snapshot.maxMs())
                .containsExactly("com.example.Service", "work", 125L);
    }

    @Test
    void samplingZeroDropsRecords() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        ProfilerRuntime.initialize(new ProfilerConfig(true, List.of("*"), List.of(), 100, Duration.ofSeconds(60), 50, 0.0, 20, 10), aggregator);

        ProfilerRuntime.record("com.example.Service", "work", "void work()", 125);

        assertThat(aggregator.topSnapshots(1)).isEmpty();
    }

    private static ProfilerConfig config() {
        return new ProfilerConfig(true, List.of("*"), List.of(), 100, Duration.ofSeconds(60), 50, 1.0, 20, 10);
    }
}
```

- [ ] **Step 2: Implement `ProfilerRuntime`**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/instrument/ProfilerRuntime.java`:

```java
package com.nowcoder.observability.methodprofiler.instrument;

import com.nowcoder.observability.methodprofiler.config.ProfilerConfig;
import com.nowcoder.observability.methodprofiler.log.ProfilerEventLogger;
import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.rate.TokenBucketRateLimiter;
import com.nowcoder.observability.methodprofiler.stats.MethodLatencyAggregator;
import com.nowcoder.observability.methodprofiler.trace.TraceContextReader;

import java.util.concurrent.ThreadLocalRandom;

public final class ProfilerRuntime {

    private static volatile ProfilerConfig config;
    private static volatile MethodLatencyAggregator aggregator;
    private static volatile ProfilerEventLogger logger;
    private static volatile TraceContextReader traceReader;
    private static volatile TokenBucketRateLimiter slowCallLimiter;

    private ProfilerRuntime() {
    }

    public static void initialize(ProfilerConfig profilerConfig, MethodLatencyAggregator latencyAggregator) {
        config = profilerConfig;
        aggregator = latencyAggregator;
        logger = new ProfilerEventLogger();
        traceReader = new TraceContextReader();
        slowCallLimiter = new TokenBucketRateLimiter(profilerConfig.maxEventsPerSecond(), System::nanoTime);
    }

    public static void record(String className, String methodName, String descriptor, long durationMs) {
        ProfilerConfig currentConfig = config;
        MethodLatencyAggregator currentAggregator = aggregator;
        if (currentConfig == null || currentAggregator == null || className == null || methodName == null) {
            return;
        }
        if (!sample(currentConfig.sampleRate())) {
            return;
        }
        MethodKey key = MethodKey.from(className, methodName, descriptor);
        currentAggregator.record(key, durationMs);
        if (durationMs >= currentConfig.slowThresholdMs()) {
            ProfilerEventLogger currentLogger = logger;
            TraceContextReader currentTraceReader = traceReader;
            TokenBucketRateLimiter limiter = slowCallLimiter;
            if (currentLogger != null && currentTraceReader != null && limiter != null && limiter.tryAcquire()) {
                currentLogger.logSlowCall(key, durationMs, currentConfig.slowThresholdMs(), currentTraceReader.currentTraceFields());
            }
        }
    }

    static void resetForTests() {
        config = null;
        aggregator = null;
        logger = null;
        traceReader = null;
        slowCallLimiter = null;
    }

    private static boolean sample(double sampleRate) {
        if (sampleRate >= 1.0) {
            return true;
        }
        if (sampleRate <= 0.0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }
}
```

- [ ] **Step 3: Implement Byte Buddy advice**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/instrument/ProfilingAdvice.java`:

```java
package com.nowcoder.observability.methodprofiler.instrument;

import net.bytebuddy.asm.Advice;

public class ProfilingAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    static long onEnter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    static void onExit(
            @Advice.Origin("#t") String className,
            @Advice.Origin("#m") String methodName,
            @Advice.Origin("#d") String descriptor,
            @Advice.Enter long startedAtNanos
    ) {
        long durationMs = Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
        ProfilerRuntime.record(className, methodName, descriptor, durationMs);
    }
}
```

- [ ] **Step 4: Wire Byte Buddy in `MethodProfilerAgent`**

Replace `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/MethodProfilerAgent.java` with:

```java
package com.nowcoder.observability.methodprofiler;

import com.nowcoder.observability.methodprofiler.config.ProfilerConfig;
import com.nowcoder.observability.methodprofiler.config.ProfilerConfigLoader;
import com.nowcoder.observability.methodprofiler.instrument.ProfilerRuntime;
import com.nowcoder.observability.methodprofiler.instrument.ProfilingAdvice;
import com.nowcoder.observability.methodprofiler.match.MethodProfilerMatcher;
import com.nowcoder.observability.methodprofiler.stats.MethodLatencyAggregator;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isBridge;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isNative;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.not;

public final class MethodProfilerAgent {

    private MethodProfilerAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        ProfilerConfig config = ProfilerConfigLoader.load(agentArgs);
        if (!config.enabled() || instrumentation == null) {
            return;
        }
        MethodProfilerMatcher matcher = new MethodProfilerMatcher(config);
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(config.maxTrackedMethods());
        ProfilerRuntime.initialize(config, aggregator);

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
                        builder.visit(Advice.to(ProfilingAdvice.class).on(not(isConstructor())
                                .and(not(isTypeInitializer()))
                                .and(not(isAbstract()))
                                .and(not(isNative()))
                                .and(not(isBridge()))
                                .and(not(isSynthetic()))
                                .and(new ElementMatcher.Junction.AbstractBase<MethodDescription>() {
                                    @Override
                                    public boolean matches(MethodDescription target) {
                                        return true;
                                    }
                                }))))
                .installOn(instrumentation);
    }
}
```

If the Byte Buddy transformer lambda signature does not match the selected Byte Buddy version, adjust only the lambda parameter list to match the compiler error. Do not change matcher behavior.

- [ ] **Step 5: Run runtime tests and compile**

Run:

```bash
cd backend
mvn -q -pl :method-profiler-agent test -Dtest=ProfilerRuntimeTest
mvn -q -pl :method-profiler-agent -DskipTests compile
```

Expected: PASS.

- [ ] **Step 6: Commit instrumentation runtime**

```bash
git add backend/method-profiler-agent
git commit -m "feat: wire method profiler instrumentation"
```

## Task 6: Summary Scheduler and Forked JVM Agent Integration Test

**Files:**
- Modify: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/MethodProfilerAgent.java`
- Create: `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/schedule/SummaryReporter.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/schedule/SummaryReporterTest.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/integration/AgentTargetMain.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/integration/AgentTargetService.java`
- Test: `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/integration/MethodProfilerAgentIT.java`

- [ ] **Step 1: Write summary reporter test**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/schedule/SummaryReporterTest.java`:

```java
package com.nowcoder.observability.methodprofiler.schedule;

import com.nowcoder.observability.methodprofiler.log.ProfilerEventLogger;
import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.stats.MethodLatencyAggregator;
import com.nowcoder.observability.methodprofiler.trace.TraceContextReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryReporterTest {

    @Test
    void reportsTopSnapshots() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        aggregator.record(new MethodKey("com.example.Service", "work", "abcdef1234567890"), 42);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SummaryReporter reporter = new SummaryReporter(
                aggregator,
                new ProfilerEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8)),
                new TraceContextReader(),
                5
        );

        reporter.reportOnce();

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"event.action\":\"method_latency_summary\"")
                .contains("\"method.class\":\"com.example.Service\"");
    }
}
```

- [ ] **Step 2: Implement `SummaryReporter`**

Create `backend/method-profiler-agent/src/main/java/com/nowcoder/observability/methodprofiler/schedule/SummaryReporter.java`:

```java
package com.nowcoder.observability.methodprofiler.schedule;

import com.nowcoder.observability.methodprofiler.log.ProfilerEventLogger;
import com.nowcoder.observability.methodprofiler.stats.MethodLatencyAggregator;
import com.nowcoder.observability.methodprofiler.trace.TraceContextReader;

import java.time.Duration;

public class SummaryReporter {

    private final MethodLatencyAggregator aggregator;
    private final ProfilerEventLogger logger;
    private final TraceContextReader traceContextReader;
    private final int topN;

    public SummaryReporter(
            MethodLatencyAggregator aggregator,
            ProfilerEventLogger logger,
            TraceContextReader traceContextReader,
            int topN
    ) {
        this.aggregator = aggregator;
        this.logger = logger;
        this.traceContextReader = traceContextReader;
        this.topN = topN;
    }

    public void start(Duration interval) {
        Thread thread = new Thread(() -> runLoop(interval), "method-profiler-summary");
        thread.setDaemon(true);
        thread.start();
    }

    public void reportOnce() {
        logger.logSummary(aggregator.topSnapshots(topN), aggregator.droppedMethodKeys(), traceContextReader.currentTraceFields());
    }

    private void runLoop(Duration interval) {
        long sleepMillis = Math.max(1_000, interval.toMillis());
        while (true) {
            try {
                Thread.sleep(sleepMillis);
                reportOnce();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ignored) {
            }
        }
    }
}
```

- [ ] **Step 3: Start summary reporter from the agent**

In `MethodProfilerAgent.premain`, after `ProfilerRuntime.initialize(config, aggregator);`, add:

```java
new com.nowcoder.observability.methodprofiler.schedule.SummaryReporter(
        aggregator,
        new com.nowcoder.observability.methodprofiler.log.ProfilerEventLogger(),
        new com.nowcoder.observability.methodprofiler.trace.TraceContextReader(),
        config.topN()
).start(config.summaryInterval());
```

- [ ] **Step 4: Add forked JVM target classes**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/integration/AgentTargetService.java`:

```java
package com.nowcoder.observability.methodprofiler.integration;

public class AgentTargetService {

    public String slowWork() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return "done";
    }

    public void throwingWork() {
        throw new IllegalStateException("target failure");
    }
}
```

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/integration/AgentTargetMain.java`:

```java
package com.nowcoder.observability.methodprofiler.integration;

public class AgentTargetMain {

    public static void main(String[] args) {
        AgentTargetService service = new AgentTargetService();
        service.slowWork();
        try {
            service.throwingWork();
        } catch (IllegalStateException expected) {
            System.err.println("target exception propagated");
        }
        try {
            Thread.sleep(1200);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 5: Add forked JVM integration test**

Create `backend/method-profiler-agent/src/test/java/com/nowcoder/observability/methodprofiler/integration/MethodProfilerAgentIT.java`:

```java
package com.nowcoder.observability.methodprofiler.integration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MethodProfilerAgentIT {

    @Test
    void agentEmitsSlowCallAndSummaryWithoutChangingTargetExceptions() throws Exception {
        Path agentJar = Path.of("target", "method-profiler-agent-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertThat(agentJar).exists();
        String classpath = System.getProperty("java.class.path");
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-javaagent:" + agentJar + "=enabled=true,includes=com.nowcoder.observability.methodprofiler.integration.*,slowThresholdMs=1,summaryInterval=1s,topN=5,maxEventsPerSecond=10");
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
                .contains("\"event.action\":\"method_slow_call\"")
                .contains("\"event.action\":\"method_latency_summary\"")
                .contains("\"method.class\":\"com.nowcoder.observability.methodprofiler.integration.AgentTargetService\"");
    }
}
```

- [ ] **Step 6: Run summary and integration tests**

Run:

```bash
cd backend
mvn -q -pl :method-profiler-agent -DskipTests package
mvn -q -pl :method-profiler-agent verify -Dtest=SummaryReporterTest -Dit.test=MethodProfilerAgentIT
```

Expected: PASS. The Failsafe phase builds the shaded agent jar before running `MethodProfilerAgentIT`, and the integration test should show slow-call and summary JSON in captured process output.

- [ ] **Step 7: Commit scheduler and integration tests**

```bash
git add backend/method-profiler-agent
git commit -m "test: verify method profiler java agent"
```

## Task 7: Backend Image and Runtime Script Wiring

**Files:**
- Modify: `deploy/Dockerfile.backend-service`
- Modify: `backend/scripts/run-backend-service.sh`
- Create: `backend/scripts/run-backend-service.test.sh`

- [ ] **Step 1: Write runtime script smoke tests**

Create `backend/scripts/run-backend-service.test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
script="${script_dir}/run-backend-service.sh"

grep -F 'method_profiler_enabled="${METHOD_PROFILER_ENABLED:-false}"' "${script}"
grep -F 'METHOD_PROFILER_ENABLED=true' "${script}"
grep -F '/otel/method-profiler-agent.jar' "${script}"
grep -F 'java_opts="${java_opts:+${java_opts} }-javaagent:/otel/method-profiler-agent.jar"' "${script}"
grep -F 'missing method profiler agent at /otel/method-profiler-agent.jar' "${script}"
```

- [ ] **Step 2: Verify the script test fails before implementation**

Run:

```bash
bash backend/scripts/run-backend-service.test.sh
```

Expected: FAIL because `run-backend-service.sh` does not yet contain method profiler wiring.

- [ ] **Step 3: Update `run-backend-service.sh`**

Modify `backend/scripts/run-backend-service.sh` so the variable block becomes:

```sh
java_opts="${JAVA_OPTS:-}"
service_version="${SERVICE_VERSION:-unknown}"
otel_enabled="${OTEL_ENABLED:-false}"
otel_service_name="${OTEL_SERVICE_NAME:-}"
otel_resource_attributes="${OTEL_RESOURCE_ATTRIBUTES:-}"
method_profiler_enabled="${METHOD_PROFILER_ENABLED:-false}"
```

After the existing OTel block, add:

```sh
if [ "${method_profiler_enabled}" = "true" ]; then
  if [ ! -f /otel/method-profiler-agent.jar ]; then
    echo "[backend-runtime] missing method profiler agent at /otel/method-profiler-agent.jar" >&2
    exit 1
  fi
  java_opts="${java_opts:+${java_opts} }-javaagent:/otel/method-profiler-agent.jar"
fi
```

- [ ] **Step 4: Update backend Dockerfile to build and copy the profiler agent**

In `deploy/Dockerfile.backend-service`, change the build command function from:

```sh
build_one() { mvn -q -DskipTests -pl ":${MODULE}" -am package; };
```

to:

```sh
build_one() { mvn -q -DskipTests -pl ":${MODULE},:method-profiler-agent" -am package; };
```

After `cp "${jar_path}" /workspace/app.jar`, add:

```sh
    profiler_agent_path="$(find ./method-profiler-agent/target -maxdepth 1 -type f -name "method-profiler-agent-*.jar" ! -name "original-*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" ! -name "*-tests.jar" -print | sort | head -n 1)"; \
    if [ -z "${profiler_agent_path}" ]; then \
      echo "[docker-build] ERROR: Cannot find built method profiler agent jar." >&2; \
      exit 1; \
    fi; \
    cp "${profiler_agent_path}" /workspace/method-profiler-agent.jar
```

After `COPY --from=build /workspace/app.jar /app/app.jar`, add:

```dockerfile
COPY --from=build /workspace/method-profiler-agent.jar /otel/method-profiler-agent.jar
```

- [ ] **Step 5: Run script and module verification**

Run:

```bash
bash backend/scripts/run-backend-service.test.sh
cd backend
mvn -q -pl :method-profiler-agent -DskipTests package
```

Expected: both PASS.

- [ ] **Step 6: Commit runtime wiring**

```bash
git add deploy/Dockerfile.backend-service backend/scripts/run-backend-service.sh backend/scripts/run-backend-service.test.sh
git commit -m "feat: wire method profiler agent runtime"
```

## Task 8: Compose Env, Deploy Tests, and Documentation

**Files:**
- Modify: `deploy/compose.runtime.services.single.yml`
- Modify: `deploy/compose.runtime.services.cluster.yml`
- Modify: `deploy/.env.single.example`
- Modify: `deploy/.env.cluster.example`
- Modify: `deploy/tests/observability_otel_default.sh`
- Modify: `docs/handbook/operations.md`
- Modify: `deploy/README.md`

- [ ] **Step 1: Extend deploy test expectations first**

In `deploy/tests/observability_otel_default.sh`, after the `OTEL_LOGS_COLLECTION` assertions, add:

```bash
if ! rg -n 'METHOD_PROFILER_ENABLED[=: ]+"?false"?|METHOD_PROFILER_ENABLED=false' "${single_config}" >/dev/null; then
  echo "expected single config to keep method profiler disabled by default" >&2
  exit 1
fi

if ! rg -n 'METHOD_PROFILER_ENABLED[=: ]+"?false"?|METHOD_PROFILER_ENABLED=false' "${cluster_config}" >/dev/null; then
  echo "expected cluster config to keep method profiler disabled by default" >&2
  exit 1
fi

if ! rg -n 'METHOD_PROFILER_INCLUDES[=: ]+"?com.nowcoder.community.\*"?|METHOD_PROFILER_INCLUDES=com.nowcoder.community.\*' "${single_config}" >/dev/null; then
  echo "expected single config to use conservative community profiler includes" >&2
  exit 1
fi

if ! rg -n 'METHOD_PROFILER_INCLUDES[=: ]+"?com.nowcoder.community.\*"?|METHOD_PROFILER_INCLUDES=com.nowcoder.community.\*' "${cluster_config}" >/dev/null; then
  echo "expected cluster config to use conservative community profiler includes" >&2
  exit 1
fi
```

- [ ] **Step 2: Verify deploy test fails before compose changes**

Run:

```bash
bash deploy/tests/observability_otel_default.sh
```

Expected: FAIL because compose files do not yet render method profiler env.

- [ ] **Step 3: Add profiler env to single topology services**

In every backend service environment block in `deploy/compose.runtime.services.single.yml`, add these lines near `OTEL_LOGS_COLLECTION`:

```yaml
    - METHOD_PROFILER_ENABLED=${METHOD_PROFILER_ENABLED:-false}
    - METHOD_PROFILER_INCLUDES=${METHOD_PROFILER_INCLUDES:-com.nowcoder.community.*}
    - METHOD_PROFILER_EXCLUDES=${METHOD_PROFILER_EXCLUDES:-}
    - METHOD_PROFILER_SLOW_THRESHOLD_MS=${METHOD_PROFILER_SLOW_THRESHOLD_MS:-100}
    - METHOD_PROFILER_SUMMARY_INTERVAL=${METHOD_PROFILER_SUMMARY_INTERVAL:-60s}
    - METHOD_PROFILER_TOP_N=${METHOD_PROFILER_TOP_N:-50}
    - METHOD_PROFILER_SAMPLE_RATE=${METHOD_PROFILER_SAMPLE_RATE:-1.0}
    - METHOD_PROFILER_MAX_EVENTS_PER_SECOND=${METHOD_PROFILER_MAX_EVENTS_PER_SECOND:-20}
    - METHOD_PROFILER_MAX_TRACKED_METHODS=${METHOD_PROFILER_MAX_TRACKED_METHODS:-10000}
```

Apply this to `community-app`, `community-oss`, `community-gateway`, `community-im-gateway`, `im-core`, and `im-realtime`.

- [ ] **Step 4: Add profiler env to cluster topology services**

In every backend service environment block in `deploy/compose.runtime.services.cluster.yml`, add the same `METHOD_PROFILER_*` block near `OTEL_LOGS_COLLECTION`. Apply it to all backend deployables and replicas in the file.

- [ ] **Step 5: Document env examples**

In `deploy/.env.single.example` and `deploy/.env.cluster.example`, after the OTel block, add:

```text
# Generic JVM method profiler agent
# - Disabled by default. Enable only for diagnostic sessions.
# - The agent is generic; this Community default keeps instrumentation scoped to project classes.
# - `METHOD_PROFILER_INCLUDES=*` is allowed but still respects hard excludes for JDK, logging, Byte Buddy, and agent packages.
METHOD_PROFILER_ENABLED=false
METHOD_PROFILER_INCLUDES=com.nowcoder.community.*
METHOD_PROFILER_EXCLUDES=
METHOD_PROFILER_SLOW_THRESHOLD_MS=100
METHOD_PROFILER_SUMMARY_INTERVAL=60s
METHOD_PROFILER_TOP_N=50
METHOD_PROFILER_SAMPLE_RATE=1.0
METHOD_PROFILER_MAX_EVENTS_PER_SECOND=20
METHOD_PROFILER_MAX_TRACKED_METHODS=10000
```

- [ ] **Step 6: Document operations usage**

In `docs/handbook/operations.md`, after the runtime logging field list, add:

```markdown
### JVM 方法级诊断

后端镜像包含通用 `method-profiler-agent`，但默认不启用。需要临时定位 JVM 内部慢方法时，可在启动前设置：

```text
METHOD_PROFILER_ENABLED=true
METHOD_PROFILER_INCLUDES=com.nowcoder.community.*
METHOD_PROFILER_SLOW_THRESHOLD_MS=100
METHOD_PROFILER_SUMMARY_INTERVAL=60s
```

该 agent 通过第二个 `-javaagent` 加载，可与 OTel Java Agent 并存。它只记录方法名、类名、签名 hash、聚合耗时和慢调用阈值事件，不记录参数、返回值、请求体、SQL bind、token 或业务载荷。

Kibana 查询：

```text
event.category : method and event.action : method_latency_summary
```

按链路排查时增加：

```text
trace.id : "<trace id>"
```

不建议在生产长期打开 `METHOD_PROFILER_INCLUDES=*`。如需扩大范围，先提高阈值、降低 `METHOD_PROFILER_SAMPLE_RATE`，并保持 `METHOD_PROFILER_MAX_EVENTS_PER_SECOND` 有界。
```

- [ ] **Step 7: Document deploy README**

In `deploy/README.md`, add a short section near the observability section:

```markdown
### Optional Method Profiler Agent

Backend images include a generic JVM method profiler agent at `/otel/method-profiler-agent.jar`. It is disabled by default. Enable it for a diagnostic run with:

```bash
METHOD_PROFILER_ENABLED=true METHOD_PROFILER_INCLUDES='com.nowcoder.community.*' ./deploy/deployment.sh up --topology single
```

The agent emits `event.category=method` logs to the same stdout -> EDOT -> Elasticsearch path as other backend logs.
```

- [ ] **Step 8: Run deploy tests**

Run:

```bash
bash deploy/tests/observability_otel_default.sh
bash deploy/tests/topology_single_cluster.sh
```

Expected: PASS.

- [ ] **Step 9: Commit deploy and docs**

```bash
git add deploy/compose.runtime.services.single.yml \
  deploy/compose.runtime.services.cluster.yml \
  deploy/.env.single.example \
  deploy/.env.cluster.example \
  deploy/tests/observability_otel_default.sh \
  docs/handbook/operations.md \
  deploy/README.md
git commit -m "docs: document method profiler diagnostics"
```

## Task 9: Final Verification

**Files:**
- No new source files expected.

- [ ] **Step 1: Run full agent module tests**

Run:

```bash
cd backend
mvn -q -pl :method-profiler-agent verify
```

Expected: PASS.

- [ ] **Step 2: Run agent package verification**

Run:

```bash
cd backend
mvn -q -pl :method-profiler-agent -DskipTests package
jar tf method-profiler-agent/target/method-profiler-agent-0.0.1-SNAPSHOT.jar | grep -F 'com/nowcoder/observability/methodprofiler/MethodProfilerAgent.class'
jar tf method-profiler-agent/target/method-profiler-agent-0.0.1-SNAPSHOT.jar | grep -F 'com/nowcoder/observability/methodprofiler/shaded/net/bytebuddy'
```

Expected: PASS. The jar contains the premain class and shaded Byte Buddy classes.

- [ ] **Step 3: Run deploy smoke tests**

Run:

```bash
bash backend/scripts/run-backend-service.test.sh
bash deploy/tests/observability_otel_default.sh
bash deploy/tests/topology_single_cluster.sh
```

Expected: PASS.

- [ ] **Step 4: Render profiler-enabled config**

Run:

```bash
METHOD_PROFILER_ENABLED=true ./deploy/deployment.sh config --topology single --env-file deploy/.env.single.example > /tmp/community-single-method-profiler.yml
rg -n 'METHOD_PROFILER_ENABLED[=: ]+"?true"?|METHOD_PROFILER_ENABLED=true' /tmp/community-single-method-profiler.yml
rg -n 'METHOD_PROFILER_INCLUDES[=: ]+"?com.nowcoder.community.\*"?|METHOD_PROFILER_INCLUDES=com.nowcoder.community.\*' /tmp/community-single-method-profiler.yml
```

Expected: both `rg` commands find rendered config entries.

- [ ] **Step 5: Run affected backend compile**

Run:

```bash
cd backend
mvn -q -DskipTests package
```

Expected: PASS for the backend reactor. If this is too slow locally, at minimum run:

```bash
cd backend
mvn -q -pl :method-profiler-agent,:community-app,:community-gateway,:community-oss,:community-im-gateway,:im-core,:im-realtime -am -DskipTests package
```

Expected: PASS.

- [ ] **Step 6: Final status check**

Run:

```bash
git status --short
```

Expected: only intended files are modified or the tree is clean after commits.

- [ ] **Step 7: Commit any final verification doc fix**

Only if verification required documentation correction:

```bash
git add docs/handbook/operations.md deploy/README.md
git commit -m "docs: clarify method profiler usage"
```
