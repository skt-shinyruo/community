# Community OTel-First Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make one OpenTelemetry trace id correlate backend responses, structured JSON stdout logs, Kafka/outbox/job boundaries, traces, and metrics through the EDOT Collector.

**Architecture:** Keep SLF4J/Logback as the application logging API, but make OpenTelemetry `Span.current().getSpanContext()` the primary runtime trace source. Backend services mirror the active OTel trace/span ids into MDC at request, async, and job boundaries so Logback can emit stable JSON stdout; EDOT Collector reads Docker stdout logs and OTLP traces/metrics, then exports to Elasticsearch/Kibana. Legacy `TraceId`/`TraceContext` remains only as a compatibility adapter for existing synchronous code.

**Tech Stack:** Java 17, Spring Boot 3.2, SLF4J, Logback, logstash-logback-encoder, OpenTelemetry Java API/Agent, Spring WebMVC/WebFlux, Spring Kafka, Docker Compose, EDOT Collector, Elasticsearch/Kibana.

---

Source spec: `docs/superpowers/specs/2026-05-16-community-otel-first-observability-design.md`

## File Structure

- `backend/pom.xml`: add OTel BOM dependency management.
- `backend/community-common/common-core/pom.xml`: add OTel API to the shared trace module.
- `backend/community-common/common-web/pom.xml`: add OTel API for servlet trace response bridge.
- `backend/community-common/common-webflux/pom.xml`: add OTel API for WebFlux trace response bridge.
- `backend/community-common/common-kafka/pom.xml`: add OTel API for Kafka trace propagation.
- `backend/community-common/common-outbox/pom.xml`: add OTel API for outbox worker spans.
- `backend/community-common/common-observability/pom.xml`: no planned dependency change; this module only contributes the shared Logback include resource.
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/OtelTraceContext.java`: new OTel trace helper.
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContext.java`: mirror active trace/span ids into MDC compatibility keys.
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextScope.java`: close OTel scopes/spans and restore legacy context.
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextSnapshot.java`: prefer active OTel context and store canonical `traceparent`.
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceId.java`: read OTel trace id before legacy ThreadLocal.
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceIdCodec.java`: add span-id extraction and deterministic traceparent construction from a span id.
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceJobRunner.java`: open OTel internal spans for scheduled jobs.
- `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/TraceIdFilter.java`: bridge servlet requests to active OTel trace context.
- `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/ResultTraceIdAdvice.java`: set `Result.traceId` from active OTel context.
- `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/SecurityExceptionHandler.java`: keep security error trace responses aligned with OTel.
- `backend/community-common/common-webflux/src/main/java/com/nowcoder/community/common/webflux/TraceIdWebFilter.java`: bridge WebFlux request/response `traceparent`.
- `backend/community-common/common-webflux/src/main/java/com/nowcoder/community/common/webflux/SecurityExceptionHandler.java`: keep WebFlux security error trace responses aligned with OTel.
- `backend/community-common/common-security/src/main/java/com/nowcoder/community/common/security/response/SecurityResponseSupport.java`: resolve security response trace id/header from active OTel context.
- `backend/community-common/common-kafka/src/main/java/com/nowcoder/community/common/kafka/trace/TraceKafkaHeaders.java`: inject/extract W3C trace context using OTel propagators.
- `backend/community-common/common-kafka/src/main/java/com/nowcoder/community/common/kafka/trace/TraceRecordInterceptor.java`: restore OTel context while Kafka listener code runs.
- `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/tx/AfterCommitExecutor.java`: capture the current OTel traceparent for after-commit callbacks.
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java`: store canonical current OTel traceparent.
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorker.java`: open child spans from stored outbox traceparent.
- `backend/community-common/common-observability/src/main/resources/logback/community-observability.xml`: shared text/json stdout Logback shape.
- `backend/community-app/src/main/resources/logback-spring.xml`
- `backend/community-gateway/src/main/resources/logback-spring.xml`
- `backend/community-oss/src/main/resources/logback-spring.xml`
- `backend/community-im-gateway/src/main/resources/logback-spring.xml`
- `backend/community-im/im-core/src/main/resources/logback-spring.xml`
- `backend/community-im/im-realtime/src/main/resources/logback-spring.xml`
- `deploy/compose.runtime.services.single.yml`: remove app log volume mounts and file-log env; use `json-logs`.
- `deploy/compose.runtime.services.cluster.yml`: same for cluster deployables.
- `deploy/compose.observability.yml`: mount Docker container log files into EDOT Collector instead of `observability_logs`.
- `deploy/compose.yml`: remove unused `observability_logs` volume definition.
- `deploy/observability/edot-collector.yml`: collect Docker stdout logs and parse JSON application lines.
- `deploy/tests/observability_otel_default.sh`: extend config assertions for stdout log collection.
- `deploy/.env.single.example`, `deploy/.env.cluster.example`: update observability comments and standardized env vars.
- `deploy/observability/kibana/README.md`, `deploy/observability/kibana/saved-objects.ndjson`: update saved-search wording from volume logs to stdout logs.
- `docs/handbook/operations.md`, `docs/handbook/local-development.md`, `docs/handbook/system-design.md`, `deploy/README.md`: document the OTel-first stdout/OTLP path.

## Task 1: Add OTel API Dependencies

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/community-common/common-core/pom.xml`
- Modify: `backend/community-common/common-web/pom.xml`
- Modify: `backend/community-common/common-webflux/pom.xml`
- Modify: `backend/community-common/common-kafka/pom.xml`
- Modify: `backend/community-common/common-outbox/pom.xml`

- [ ] **Step 1: Add parent dependency management**

In `backend/pom.xml`, add the property:

```xml
<opentelemetry.version>1.50.0</opentelemetry.version>
```

Add this import under `<dependencyManagement><dependencies>` after the Spring Cloud Alibaba import:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-bom</artifactId>
    <version>${opentelemetry.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

- [ ] **Step 2: Add direct OTel API dependencies**

Add this dependency to `backend/community-common/common-core/pom.xml`, `backend/community-common/common-web/pom.xml`, `backend/community-common/common-webflux/pom.xml`, `backend/community-common/common-kafka/pom.xml`, and `backend/community-common/common-outbox/pom.xml`:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
</dependency>
```

- [ ] **Step 3: Verify dependency resolution**

Run:

```bash
cd backend
mvn -q -pl :community-common-core,:community-common-web,:community-common-webflux,:community-common-kafka,:community-common-outbox -DskipTests compile
```

Expected: PASS. If Maven downloads dependencies, that is acceptable. If it fails with duplicate or missing OTel classes, stop and fix dependency management before changing trace code.

- [ ] **Step 4: Commit dependencies**

```bash
git add backend/pom.xml \
  backend/community-common/common-core/pom.xml \
  backend/community-common/common-web/pom.xml \
  backend/community-common/common-webflux/pom.xml \
  backend/community-common/common-kafka/pom.xml \
  backend/community-common/common-outbox/pom.xml
git commit -m "build: add opentelemetry api dependencies"
```

## Task 2: Make Common Trace Context OTel-First

**Files:**
- Create: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/OtelTraceContext.java`
- Modify: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContext.java`
- Modify: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextScope.java`
- Modify: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextSnapshot.java`
- Modify: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceId.java`
- Modify: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceIdCodec.java`
- Modify: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceJobRunner.java`
- Test: `backend/community-common/common-core/src/test/java/com/nowcoder/community/common/trace/TraceContextSnapshotTest.java`
- Test: `backend/community-common/common-core/src/test/java/com/nowcoder/community/common/trace/TraceJobRunnerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/common/trace/TraceIdCodecTest.java`

- [ ] **Step 1: Write failing tests for OTel current-span preference**

In `TraceContextSnapshotTest`, add:

```java
@Test
void currentOrNewShouldUseActiveOtelSpanBeforeLegacyThreadLocal() {
    TraceContext.set("11111111111111111111111111111111");
    SpanContext spanContext = SpanContext.create(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "bbbbbbbbbbbbbbbb",
            TraceFlags.getSampled(),
            TraceState.getDefault()
    );

    try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
        TraceContextSnapshot snapshot = TraceContextSnapshot.currentOrNew();

        assertThat(snapshot.traceId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(snapshot.spanId()).isEqualTo("bbbbbbbbbbbbbbbb");
        assertThat(snapshot.traceparent()).isEqualTo("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");
        assertThat(TraceId.get()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }
}
```

Add imports:

```java
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
```

Also update existing assertions in `TraceContextSnapshotTest` that read `MDC.get("traceId")` so they read `MDC.get(TraceContext.MDC_KEY_TRACE_ID)` and, when useful, assert `MDC.get(TraceContext.MDC_KEY_LEGACY_TRACE_ID)` separately. The canonical logging key after this task is `trace.id`.

- [ ] **Step 2: Write failing tests for span id parsing**

In `TraceIdCodecTest`, add:

```java
@Test
void extractSpanIdFromTraceparentShouldReturnValidSpanId() {
    String spanId = TraceIdCodec.extractSpanIdFromTraceparent(traceparent(TRACEPARENT_TRACE_ID));

    assertThat(spanId).isEqualTo("00f067aa0ba902b7");
}

@Test
void buildTraceparentShouldUseProvidedSpanIdAndFlags() {
    String traceparent = TraceIdCodec.buildTraceparent(
            TRACEPARENT_TRACE_ID,
            "00f067aa0ba902b7",
            "00"
    );

    assertThat(traceparent).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00");
}
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```bash
cd backend
mvn -q -pl :community-common-core,:community-app -Dtest='TraceContextSnapshotTest,TraceIdCodecTest' test
```

Expected: FAIL because `spanId()`, `extractSpanIdFromTraceparent`, and the three-argument `buildTraceparent` do not exist yet.

- [ ] **Step 4: Create `OtelTraceContext`**

Create `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/OtelTraceContext.java`:

```java
package com.nowcoder.community.common.trace;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OtelTraceContext {

    public static final String INSTRUMENTATION_NAME = "com.nowcoder.community";

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier == null ? List.of() : carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            if (carrier == null || key == null) {
                return null;
            }
            return carrier.get(key);
        }
    };

    private static final TextMapSetter<Map<String, String>> MAP_SETTER = (carrier, key, value) -> {
        if (carrier != null && key != null && value != null) {
            carrier.put(key, value);
        }
    };

    private OtelTraceContext() {
    }

    public static SpanContext currentSpanContext() {
        SpanContext spanContext = Span.current().getSpanContext();
        return spanContext.isValid() ? spanContext : null;
    }

    public static String currentTraceId() {
        SpanContext spanContext = currentSpanContext();
        return spanContext == null ? null : spanContext.getTraceId();
    }

    public static String currentSpanId() {
        SpanContext spanContext = currentSpanContext();
        return spanContext == null ? null : spanContext.getSpanId();
    }

    public static String currentTraceparent() {
        SpanContext spanContext = currentSpanContext();
        return spanContext == null ? null : traceparent(spanContext);
    }

    public static String traceparent(SpanContext spanContext) {
        if (spanContext == null || !spanContext.isValid()) {
            return null;
        }
        return TraceIdCodec.buildTraceparent(
                spanContext.getTraceId(),
                spanContext.getSpanId(),
                spanContext.getTraceFlags().asHex()
        );
    }

    public static Context extract(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return Context.current();
        }
        return W3CTraceContextPropagator.getInstance()
                .extract(Context.current(), Map.of(TraceHeaders.HEADER_TRACEPARENT, traceparent.trim()), MAP_GETTER);
    }

    public static void inject(Context context, Map<String, String> carrier) {
        W3CTraceContextPropagator.getInstance()
                .inject(context == null ? Context.current() : context, carrier, MAP_SETTER);
    }

    public static TraceContextScope openForInbound(String traceparent, String spanName, SpanKind spanKind) {
        SpanContext active = currentSpanContext();
        if (active != null) {
            return TraceContextScope.open(TraceContextSnapshot.fromSpanContext(active, false), Scope.noop(), null);
        }

        Context parent = extract(traceparent);
        SpanContext extracted = Span.fromContext(parent).getSpanContext();
        SpanBuilder builder = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME)
                .spanBuilder(safeSpanName(spanName))
                .setSpanKind(spanKind == null ? SpanKind.INTERNAL : spanKind);
        if (extracted.isValid()) {
            builder.setParent(parent);
        } else {
            builder.setNoParent();
        }

        Span span = builder.startSpan();
        SpanContext started = span.getSpanContext();
        if (started.isValid()) {
            Scope scope = span.makeCurrent();
            return TraceContextScope.open(TraceContextSnapshot.fromSpanContext(started, false), scope, span);
        }

        if (extracted.isValid()) {
            Scope scope = Span.wrap(extracted).makeCurrent();
            return TraceContextScope.open(TraceContextSnapshot.fromSpanContext(extracted, true), scope, null);
        }

        return TraceContextScope.open(TraceContextSnapshot.synthetic(), Scope.noop(), null);
    }

    public static TraceContextScope openInternalSpan(String spanName) {
        SpanContext active = currentSpanContext();
        SpanBuilder builder = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME)
                .spanBuilder(safeSpanName(spanName))
                .setSpanKind(SpanKind.INTERNAL);
        if (active == null) {
            builder.setNoParent();
        }
        Span span = builder.startSpan();
        SpanContext started = span.getSpanContext();
        if (started.isValid()) {
            Scope scope = span.makeCurrent();
            return TraceContextScope.open(TraceContextSnapshot.fromSpanContext(started, false), scope, span);
        }
        if (active != null) {
            return TraceContextScope.open(TraceContextSnapshot.fromSpanContext(active, false), Scope.noop(), null);
        }
        return TraceContextScope.open(TraceContextSnapshot.synthetic(), Scope.noop(), null);
    }

    private static String safeSpanName(String spanName) {
        if (spanName == null || spanName.isBlank()) {
            return "community.internal";
        }
        return spanName.trim().toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 5: Update trace compatibility classes**

Update `TraceContext` so it mirrors both canonical and legacy MDC keys:

```java
public static final String MDC_KEY_TRACE_ID = "trace.id";
public static final String MDC_KEY_SPAN_ID = "span.id";
public static final String MDC_KEY_LEGACY_TRACE_ID = "traceId";
```

Replace `set(String traceId)` with:

```java
public static void set(String traceId) {
    set(traceId, null);
}

public static void set(String traceId, String spanId) {
    if (traceId == null || traceId.isBlank()) {
        return;
    }
    String t = traceId.trim();
    if (t.isEmpty()) {
        return;
    }
    TraceId.set(t);
    MDC.put(MDC_KEY_TRACE_ID, t);
    MDC.put(MDC_KEY_LEGACY_TRACE_ID, t);
    String normalizedSpanId = TraceIdCodec.normalizeSpanId(spanId);
    if (normalizedSpanId != null) {
        MDC.put(MDC_KEY_SPAN_ID, normalizedSpanId);
    }
}
```

Replace `clear()` with:

```java
public static void clear() {
    MDC.remove(MDC_KEY_TRACE_ID);
    MDC.remove(MDC_KEY_SPAN_ID);
    MDC.remove(MDC_KEY_LEGACY_TRACE_ID);
    TraceId.clear();
}
```

In `TraceId`, make `get()` OTel-first and add a package-private raw accessor:

```java
public static String get() {
    String current = OtelTraceContext.currentTraceId();
    return current == null ? CURRENT.get() : current;
}

static String threadLocalValue() {
    return CURRENT.get();
}
```

- [ ] **Step 6: Update `TraceIdCodec`**

Add:

```java
public static String normalizeSpanId(String spanId) {
    if (spanId == null || spanId.isBlank()) {
        return null;
    }
    String s = spanId.trim();
    if (!isHex(s, 16) || isAllZeros(s)) {
        return null;
    }
    return s.toLowerCase();
}

public static String extractSpanIdFromTraceparent(String traceparent) {
    if (traceparent == null || traceparent.isBlank()) {
        return null;
    }
    String[] parts = traceparent.trim().split("-");
    if (parts.length != 4 || extractTraceIdFromTraceparent(traceparent) == null) {
        return null;
    }
    return normalizeSpanId(parts[2]);
}

public static String buildTraceparent(String traceId, String spanId, String flags) {
    String t = normalizeTraceId(traceId);
    String s = normalizeSpanId(spanId);
    String f = flags == null ? null : flags.trim().toLowerCase();
    if (t == null) {
        t = generateTraceId();
    }
    if (s == null) {
        s = generateSpanId();
    }
    if (f == null || !isHex(f, 2)) {
        f = "01";
    }
    return "00-" + t + "-" + s + "-" + f;
}

private static String generateSpanId() {
    String spanId = Long.toHexString(UUID.randomUUID().getMostSignificantBits()).replace("-", "");
    if (spanId.length() < 16) {
        spanId = "0".repeat(16 - spanId.length()) + spanId;
    } else if (spanId.length() > 16) {
        spanId = spanId.substring(spanId.length() - 16);
    }
    if (isAllZeros(spanId)) {
        return "0000000000000001";
    }
    return spanId.toLowerCase();
}
```

Change the existing one-argument `buildTraceparent(String traceId)` to:

```java
public static String buildTraceparent(String traceId) {
    return buildTraceparent(traceId, null, "01");
}
```

- [ ] **Step 7: Update snapshots and scopes**

Add this method to `TraceContextSnapshot`:

```java
public String spanId() {
    return TraceIdCodec.extractSpanIdFromTraceparent(traceparent);
}
```

Add these factories:

```java
public static TraceContextSnapshot fromSpanContext(SpanContext spanContext, boolean recovered) {
    if (spanContext == null || !spanContext.isValid()) {
        return synthetic();
    }
    return new TraceContextSnapshot(
            spanContext.getTraceId(),
            OtelTraceContext.traceparent(spanContext),
            recovered
    );
}

public static TraceContextSnapshot synthetic() {
    return new TraceContextSnapshot(TraceIdCodec.generateTraceId(), null, true);
}
```

Change `currentOrNew()` to:

```java
public static TraceContextSnapshot currentOrNew() {
    SpanContext spanContext = OtelTraceContext.currentSpanContext();
    if (spanContext != null) {
        return fromSpanContext(spanContext, false);
    }
    String current = TraceIdCodec.normalizeTraceId(TraceId.threadLocalValue());
    boolean recovered = current == null;
    return new TraceContextSnapshot(current, null, recovered);
}
```

Update `TraceContextScope` to store `Scope otelScope` and `Span span`. Add an overloaded `open`:

```java
static TraceContextScope open(TraceContextSnapshot snapshot, Scope otelScope, Span span) {
    TraceContextScope scope = new TraceContextScope(
            TraceId.threadLocalValue(),
            MDC.get(TraceContext.MDC_KEY_TRACE_ID),
            MDC.get(TraceContext.MDC_KEY_SPAN_ID),
            MDC.get(TraceContext.MDC_KEY_LEGACY_TRACE_ID),
            otelScope,
            span
    );
    if (snapshot != null) {
        TraceContext.set(snapshot.traceId(), snapshot.spanId());
    }
    return scope;
}
```

Keep `static TraceContextScope open(TraceContextSnapshot snapshot)` and delegate to the new overload with `Scope.noop()` and `null`.

In `close()`, clear compatibility context, restore prior MDC values, then close/end the OTel objects:

```java
try {
    TraceContext.clear();
    if (previousTraceId != null) {
        TraceId.set(previousTraceId);
    }
    restore(TraceContext.MDC_KEY_TRACE_ID, previousMdcTraceId);
    restore(TraceContext.MDC_KEY_SPAN_ID, previousMdcSpanId);
    restore(TraceContext.MDC_KEY_LEGACY_TRACE_ID, previousLegacyMdcTraceId);
} finally {
    try {
        if (otelScope != null) {
            otelScope.close();
        }
    } finally {
        if (span != null) {
            span.end();
        }
    }
}
```

Add the helper used by `close()`:

```java
private void restore(String key, String previousValue) {
    if (previousValue == null) {
        MDC.remove(key);
        return;
    }
    MDC.put(key, previousValue);
}
```

- [ ] **Step 8: Update job runner**

Change `TraceJobRunner.run` to:

```java
public static void run(String jobName, Runnable action) {
    if (action == null) {
        return;
    }
    try (TraceContextScope ignored = OtelTraceContext.openInternalSpan(jobName)) {
        action.run();
    }
}
```

- [ ] **Step 9: Run focused common trace tests**

Run:

```bash
cd backend
mvn -q -pl :community-common-core,:community-app -Dtest='TraceContextSnapshotTest,TraceJobRunnerTest,TraceIdCodecTest' test
```

Expected: PASS.

- [ ] **Step 10: Commit common trace changes**

```bash
git add backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace \
  backend/community-common/common-core/src/test/java/com/nowcoder/community/common/trace \
  backend/community-app/src/test/java/com/nowcoder/community/common/trace/TraceIdCodecTest.java
git commit -m "feat: make common trace context otel-first"
```

## Task 3: Rewire Servlet and WebFlux Trace Response Bridges

**Files:**
- Modify: `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/TraceIdFilter.java`
- Modify: `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/ResultTraceIdAdvice.java`
- Modify: `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/SecurityExceptionHandler.java`
- Modify: `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/AuditLogFilter.java`
- Modify: `backend/community-common/common-webflux/src/main/java/com/nowcoder/community/common/webflux/TraceIdWebFilter.java`
- Modify: `backend/community-common/common-webflux/src/main/java/com/nowcoder/community/common/webflux/SecurityExceptionHandler.java`
- Modify: `backend/community-common/common-security/src/main/java/com/nowcoder/community/common/security/response/SecurityResponseSupport.java`
- Create: `backend/community-common/common-web/src/test/java/com/nowcoder/community/common/web/TraceIdFilterTest.java`
- Modify: `backend/community-common/common-web/src/test/java/com/nowcoder/community/common/web/ResultTraceIdAdviceTest.java`
- Modify: `backend/community-common/common-web/src/test/java/com/nowcoder/community/common/web/SecurityExceptionHandlerTest.java`
- Modify: `backend/community-common/common-webflux/src/test/java/com/nowcoder/community/common/webflux/TraceIdWebFilterTest.java`
- Modify: `backend/community-common/common-webflux/src/test/java/com/nowcoder/community/common/webflux/SecurityExceptionHandlerTest.java`
- Modify: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/AccessLogWebFilterTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/web/AuditLogFilterTest.java`

- [ ] **Step 1: Write servlet filter test for active OTel span**

Create `TraceIdFilterTest`:

```java
package com.nowcoder.community.common.web;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceId;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void filterShouldExposeActiveOtelTraceIdAndTraceparent() throws Exception {
        SpanContext spanContext = SpanContext.create(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbb",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
            filter.doFilter(request, response, new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
                @Override
                protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse resp) {
                    assertThat(TraceId.get()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
                    assertThat(MDC.get("trace.id")).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
                    assertThat(MDC.get("span.id")).isEqualTo("bbbbbbbbbbbbbbbb");
                }
            }));
        }

        assertThat(response.getHeader(TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");
        assertThat(TraceId.get()).isNull();
        assertThat(MDC.get("trace.id")).isNull();
        assertThat(MDC.get("span.id")).isNull();
    }
}
```

- [ ] **Step 2: Update existing response/security tests to use OTel spans**

In `ResultTraceIdAdviceTest`, replace `TraceId.set("ABCDEFABCDEFABCDEFABCDEFABCDEFAB")` with:

```java
SpanContext spanContext = SpanContext.create(
        "abcdefabcdefabcdefabcdefabcdefab",
        "1234567890abcdef",
        TraceFlags.getSampled(),
        TraceState.getDefault()
);
try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
    Object body = advice.beforeBodyWrite(
            payload,
            returnType("plainPayload"),
            null,
            null,
            new ServletServerHttpRequest(servletRequest),
            response
    );

    assertThat(body).isInstanceOf(Result.class);
    Result<?> result = (Result<?>) body;
    assertThat(result.getTraceId()).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
    assertThat(response.getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT))
            .isEqualTo("00-abcdefabcdefabcdefabcdefabcdefab-1234567890abcdef-01");
}
```

Use the same active-span pattern in servlet and WebFlux `SecurityExceptionHandlerTest`.

- [ ] **Step 3: Run HTTP tests to verify failure**

Run:

```bash
cd backend
mvn -q -pl :community-common-web,:community-common-webflux,:community-common-security,:community-gateway,:community-app -Dtest='TraceIdFilterTest,ResultTraceIdAdviceTest,SecurityExceptionHandlerTest,TraceIdWebFilterTest,AccessLogWebFilterTest,AuditLogFilterTest' test
```

Expected: FAIL because HTTP code still uses repository-generated trace ids and generated traceparent span ids.

- [ ] **Step 4: Update servlet `TraceIdFilter`**

Replace the body of `doFilter` with:

```java
HttpServletRequest req = (HttpServletRequest) request;
HttpServletResponse resp = (HttpServletResponse) response;

String spanName = "http " + (req.getMethod() == null ? "request" : req.getMethod().toLowerCase());
try (TraceContextScope ignored = OtelTraceContext.openForInbound(
        req.getHeader(TraceHeaders.HEADER_TRACEPARENT),
        spanName,
        SpanKind.SERVER
)) {
    TraceContextSnapshot snapshot = TraceContextSnapshot.currentOrNew();
    resp.setHeader(TraceHeaders.HEADER_TRACEPARENT, snapshot.traceparent());
    chain.doFilter(request, response);
}
```

Add imports for `OtelTraceContext`, `TraceContextScope`, `TraceContextSnapshot`, and `SpanKind`. Remove `TraceIdCodec` usage from this class.

- [ ] **Step 5: Update WebFlux `TraceIdWebFilter`**

Replace `filter` with:

```java
public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (exchange == null || chain == null) {
        return Mono.empty();
    }

    String existingTraceparent = exchange.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT);
    String method = exchange.getRequest().getMethod() == null
            ? "request"
            : exchange.getRequest().getMethod().name().toLowerCase();
    TraceContextScope scope = OtelTraceContext.openForInbound(existingTraceparent, "http " + method, SpanKind.SERVER);
    TraceContextSnapshot snapshot = TraceContextSnapshot.currentOrNew();

    ServerHttpRequest mutatedRequest = exchange.getRequest()
            .mutate()
            .headers(headers -> headers.set(TraceHeaders.HEADER_TRACEPARENT, snapshot.traceparent()))
            .build();
    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
    mutatedExchange.getResponse().getHeaders().set(TraceHeaders.HEADER_TRACEPARENT, snapshot.traceparent());

    return chain.filter(mutatedExchange).doFinally(signal -> scope.close());
}
```

Add imports for `OtelTraceContext`, `TraceContextScope`, `TraceContextSnapshot`, and `SpanKind`. Remove `TraceIdCodec` usage from this class.

- [ ] **Step 6: Update response trace filling**

In `ResultTraceIdAdvice.fillTraceId`, replace the trace resolution logic with:

```java
TraceContextSnapshot snapshot = TraceContextSnapshot.currentOrNew();
String traceId = snapshot.traceId();
if (traceId != null && !traceId.isBlank()) {
    result.setTraceId(traceId);
    if (response != null) {
        response.getHeaders().set(TraceHeaders.HEADER_TRACEPARENT, snapshot.traceparent());
    }
}
```

Remove imports for `SecurityResponseSupport`, `TraceId`, and `TraceIdCodec` if they are no longer used.

- [ ] **Step 7: Update security response support**

In `SecurityResponseSupport.resolveTraceId`, prefer active OTel:

```java
    String active = OtelTraceContext.currentTraceId();
    if (active != null) {
        return active;
    }
String normalizedCurrent = TraceIdCodec.normalizeTraceId(currentTraceId);
if (normalizedCurrent != null) {
    return normalizedCurrent;
}
String extracted = TraceIdCodec.extractTraceIdFromTraceparent(traceparent);
if (extracted != null) {
    return extracted;
}
return TraceContextSnapshot.currentOrNew().traceId();
```

In `build`, set the response header from the active OTel context when it exists, otherwise build a header from the resolved id:

```java
String activeTraceparent = OtelTraceContext.currentTraceparent();
String resolvedTraceId = resolveTraceId(traceId, activeTraceparent);
if (resolvedTraceId != null && !resolvedTraceId.isBlank()) {
    body.setTraceId(resolvedTraceId);
    if (headerWriter != null) {
        String headerTraceparent = activeTraceparent;
        if (!resolvedTraceId.equals(TraceIdCodec.extractTraceIdFromTraceparent(headerTraceparent))) {
            headerTraceparent = TraceIdCodec.buildTraceparent(resolvedTraceId);
        }
        headerWriter.accept(TraceHeaders.HEADER_TRACEPARENT, headerTraceparent);
    }
}
```

- [ ] **Step 8: Keep audit/access logs reading compatibility trace**

In `AuditLogFilter`, keep `String traceId = TraceId.get();`. After Task 2, this reads active OTel first.

In `AccessLogWebFilter.resolveTraceId`, prefer active OTel:

```java
String active = OtelTraceContext.currentTraceId();
if (active != null) {
    return active;
}
```

Then keep the existing response/request traceparent fallback.

- [ ] **Step 9: Run HTTP tests**

Run:

```bash
cd backend
mvn -q -pl :community-common-web,:community-common-webflux,:community-common-security,:community-gateway,:community-app -Dtest='TraceIdFilterTest,ResultTraceIdAdviceTest,SecurityExceptionHandlerTest,TraceIdWebFilterTest,AccessLogWebFilterTest,AuditLogFilterTest,GlobalExceptionHandlerTest' test
```

Expected: PASS.

- [ ] **Step 10: Commit HTTP trace bridge**

```bash
git add backend/community-common/common-web \
  backend/community-common/common-webflux \
  backend/community-common/common-security \
  backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/AccessLogWebFilter.java \
  backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/AccessLogWebFilterTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/infra/web/AuditLogFilterTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/infra/web/GlobalExceptionHandlerTest.java
git commit -m "feat: bridge http trace ids from opentelemetry"
```

## Task 4: Rewire Kafka, Outbox, Jobs, and After-Commit Trace Propagation

**Files:**
- Modify: `backend/community-common/common-kafka/src/main/java/com/nowcoder/community/common/kafka/trace/TraceKafkaHeaders.java`
- Modify: `backend/community-common/common-kafka/src/main/java/com/nowcoder/community/common/kafka/trace/TraceKafkaSender.java`
- Modify: `backend/community-common/common-kafka/src/main/java/com/nowcoder/community/common/kafka/trace/TraceRecordInterceptor.java`
- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java`
- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorker.java`
- Modify: `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/tx/AfterCommitExecutor.java`
- Modify tests under `backend/community-common/common-kafka/src/test/java/com/nowcoder/community/common/kafka/trace`
- Modify tests under `backend/community-app/src/test/java/com/nowcoder/community/infra/outbox`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/tx/AfterCommitExecutorTest.java`
- Modify IM/app outbox handler tests that call `TraceContextSnapshot.fromStored(...).open()`.

- [ ] **Step 1: Write failing Kafka sender test for active OTel span**

In `TraceKafkaSenderTest`, replace `TraceContext.set(...)` with:

```java
SpanContext spanContext = SpanContext.create(
        "abababababababababababababababab",
        "1234567890abcdef",
        TraceFlags.getSampled(),
        TraceState.getDefault()
);
try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
    TraceKafkaSender.send(kafkaTemplate, "topic-a", "key-1", "value-1");
}
```

Change the assertion to:

```java
assertThat(TraceKafkaHeaders.headerValue(record.headers(), TraceHeaders.HEADER_TRACEPARENT))
        .isEqualTo("00-abababababababababababababababab-1234567890abcdef-01");
```

- [ ] **Step 2: Write failing outbox store test for canonical traceparent**

In `JdbcOutboxEventStoreTest`, wrap enqueue with:

```java
SpanContext spanContext = SpanContext.create(
        "dddddddddddddddddddddddddddddddd",
        "1234567890abcdef",
        TraceFlags.getSampled(),
        TraceState.getDefault()
);
try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
    store.enqueue("event-1", "topic-a", "key-1", "{}");
}
```

Assert:

```java
assertThat(ev.traceId()).isEqualTo("dddddddddddddddddddddddddddddddd");
assertThat(ev.traceparent()).isEqualTo("00-dddddddddddddddddddddddddddddddd-1234567890abcdef-01");
```

- [ ] **Step 3: Run async trace tests to verify failure**

Run:

```bash
cd backend
mvn -q -pl :community-common-kafka,:community-common-outbox,:community-app,:im-core,:im-realtime -Dtest='TraceKafkaHeadersTest,TraceKafkaSenderTest,TraceRecordInterceptorTest,JdbcOutboxEventStoreTest,OutboxWorkerRetryTest,AfterCommitExecutorTest,*Outbox*HandlerTest,CommandProducerTest' test
```

Expected: FAIL because Kafka/outbox still build traceparent from legacy ThreadLocal and generated span ids.

- [ ] **Step 4: Update Kafka header injection**

Add this overload to `TraceKafkaHeaders`:

```java
public static void inject(Headers headers) {
    if (headers == null) {
        return;
    }
    TraceContextSnapshot snapshot = TraceContextSnapshot.currentOrNew();
    put(headers, TraceHeaders.HEADER_TRACEPARENT, snapshot.traceparent());
}
```

Keep `inject(Headers headers, TraceContextSnapshot snapshot)`, but make it write `snapshot.traceparent()` only. In `TraceKafkaSender.send`, call:

```java
TraceKafkaHeaders.inject(record.headers());
```

- [ ] **Step 5: Update Kafka listener scope**

In `TraceRecordInterceptor.intercept`, open a consumer span from the extracted traceparent:

```java
closeCurrentScope();
TraceContextSnapshot snapshot = record == null
        ? TraceContextSnapshot.currentOrNew()
        : TraceKafkaHeaders.extract(record.headers());
String spanName = record == null ? "kafka.consume" : "kafka.consume " + record.topic();
currentScope.set(OtelTraceContext.openForInbound(snapshot.traceparent(), spanName, SpanKind.CONSUMER));
return record;
```

Add imports for `OtelTraceContext` and `SpanKind`.

- [ ] **Step 6: Update outbox worker scope**

In `OutboxWorker`, replace:

```java
try (var ignored = TraceContextSnapshot.fromStored(event.traceId(), event.traceparent()).open()) {
```

with:

```java
TraceContextSnapshot snapshot = TraceContextSnapshot.fromStored(event.traceId(), event.traceparent());
try (var ignored = OtelTraceContext.openForInbound(
        snapshot.traceparent(),
        "outbox.process " + event.topic(),
        SpanKind.CONSUMER
)) {
```

Add imports for `OtelTraceContext` and `SpanKind`.

- [ ] **Step 7: Update after-commit trace restoration**

`JdbcOutboxEventStore` should still capture the current snapshot at enqueue time:

```java
TraceContextSnapshot trace = TraceContextSnapshot.currentOrNew();
```

Task 2 made that method OTel-first, so no extra outbox store code is needed beyond the test update.

Change `AfterCommitExecutor` to open the captured traceparent through OTel when the callback runs:

```java
TraceContextSnapshot captured = TraceContextSnapshot.currentOrNew();
Runnable tracedAction = () -> {
    try (TraceContextScope ignored = OtelTraceContext.openForInbound(
            captured.traceparent(),
            "tx.after_commit",
            SpanKind.INTERNAL
    )) {
        action.run();
    }
};
```

Add imports for `OtelTraceContext`, `TraceContextScope`, and `SpanKind`.

- [ ] **Step 8: Run async trace tests**

Run:

```bash
cd backend
mvn -q -pl :community-common-kafka,:community-common-outbox,:community-app,:im-core,:im-realtime -Dtest='TraceKafkaHeadersTest,TraceKafkaSenderTest,TraceRecordInterceptorTest,JdbcOutboxEventStoreTest,OutboxWorkerRetryTest,AfterCommitExecutorTest,*Outbox*HandlerTest,CommandProducerTest' test
```

Expected: PASS.

- [ ] **Step 9: Commit async trace propagation**

```bash
git add backend/community-common/common-kafka \
  backend/community-common/common-outbox \
  backend/community-common/common-spring \
  backend/community-app/src/test/java/com/nowcoder/community/infra/outbox \
  backend/community-app/src/test/java/com/nowcoder/community/infra/tx/AfterCommitExecutorTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/im/projection \
  backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/outbox \
  backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/kafka
git commit -m "feat: propagate otel trace context across async boundaries"
```

## Task 5: Move Backend Logging to Shared JSON Stdout

**Files:**
- Create: `backend/community-common/common-observability/src/main/resources/logback/community-observability.xml`
- Replace contents of the six deployable `logback-spring.xml` files listed in File Structure.
- Modify JSON log tests that initialize `logback-spring.xml`.

- [ ] **Step 1: Extend JSON logging tests for shared stdout shape**

In `backend/community-app/src/test/java/com/nowcoder/community/infra/observability/RuntimeObservabilityIntegrationTest.java`, update `runtimeEventsAreWrittenAsStructuredJson` so it sets a trace context before writing the event:

```java
TraceContext.set("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb");
```

Add these assertions after the existing service/version assertions:

```java
assertThat(event.path("service.namespace").asText()).isEqualTo("community");
assertThat(event.path("deployment.environment").asText()).isEqualTo("test");
assertThat(event.path("trace.id").asText()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
assertThat(event.path("span.id").asText()).isEqualTo("bbbbbbbbbbbbbbbb");
assertThat(event.has("traceId")).isFalse();
```

In `initializeProductionLogging`, add:

```java
environment.setProperty("community.logging.deployment-environment", "test");
```

In `tearDown`, add:

```java
TraceContext.clear();
```

- [ ] **Step 2: Run JSON logging test to verify failure**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='RuntimeObservabilityIntegrationTest' test
```

Expected: FAIL because current per-module Logback JSON does not emit `service.namespace`, `deployment.environment`, or canonical `span.id`.

- [ ] **Step 3: Create shared Logback include**

Create `backend/community-common/common-observability/src/main/resources/logback/community-observability.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<included>
    <springProperty scope="context" name="serviceName" source="spring.application.name" defaultValue="unknown-service"/>
    <springProperty scope="context" name="serviceVersion" source="community.logging.service-version" defaultValue="unknown"/>
    <springProperty scope="context" name="deploymentEnvironment" source="community.logging.deployment-environment" defaultValue="local"/>

    <property name="TEXT_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] [service.name=${serviceName} service.version=${serviceVersion} deployment.environment=${deploymentEnvironment} trace.id=%X{trace.id:-} span.id=%X{span.id:-}] %logger - %msg%n%ex"/>

    <appender name="CONSOLE_TEXT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
            <charset>UTF-8</charset>
            <pattern>${TEXT_PATTERN}</pattern>
        </encoder>
    </appender>

    <appender name="CONSOLE_JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp>
                    <fieldName>@timestamp</fieldName>
                    <timeZone>UTC</timeZone>
                </timestamp>
                <pattern>
                    <pattern>{"service.name":"${serviceName}","service.version":"${serviceVersion}","service.namespace":"community","deployment.environment":"${deploymentEnvironment}"}</pattern>
                </pattern>
                <mdc>
                    <excludeMdcKeyName>traceId</excludeMdcKeyName>
                </mdc>
                <logLevel>
                    <fieldName>level</fieldName>
                </logLevel>
                <loggerName>
                    <fieldName>logger</fieldName>
                </loggerName>
                <message/>
                <stackTrace>
                    <fieldName>stack_trace</fieldName>
                </stackTrace>
            </providers>
        </encoder>
    </appender>

    <springProfile name="!json-logs &amp; (default | local | dev | test)">
        <root level="INFO">
            <appender-ref ref="CONSOLE_TEXT"/>
        </root>
    </springProfile>

    <springProfile name="json-logs | (!default &amp; !local &amp; !dev &amp; !test)">
        <root level="INFO">
            <appender-ref ref="CONSOLE_JSON"/>
        </root>
    </springProfile>
</included>
```

- [ ] **Step 4: Replace deployable `logback-spring.xml` files**

Replace each of these files with the same content:

- `backend/community-app/src/main/resources/logback-spring.xml`
- `backend/community-gateway/src/main/resources/logback-spring.xml`
- `backend/community-oss/src/main/resources/logback-spring.xml`
- `backend/community-im-gateway/src/main/resources/logback-spring.xml`
- `backend/community-im/im-core/src/main/resources/logback-spring.xml`
- `backend/community-im/im-realtime/src/main/resources/logback-spring.xml`

Use:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="logback/community-observability.xml"/>
</configuration>
```

- [ ] **Step 5: Update other JSON logging tests**

In tests that build `MockEnvironment` for JSON logging, add:

```java
environment.setProperty("community.logging.deployment-environment", "test");
```

Add assertions where JSON is parsed:

```java
assertThat(event.path("service.namespace").asText()).isEqualTo("community");
assertThat(event.path("deployment.environment").asText()).isEqualTo("test");
```

For tests that previously expected `trace.id` from `%mdc{traceId}`, either create an active OTel span or keep `TraceContext.set("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")`; the shared JSON layout must emit both `trace.id` and `span.id` from MDC when present.

- [ ] **Step 6: Run JSON logging tests**

Run:

```bash
cd backend
mvn -q -pl :community-app,:community-gateway,:community-oss,:community-im-gateway,:im-core,:im-realtime -Dtest='RuntimeObservabilityIntegrationTest,AuditLogFilterTest,GlobalExceptionHandlerTest,AccessLogWebFilterTest,ObservedObjectStoreTest,CommandConsumersLoggingTest' test
```

Expected: PASS. Also verify no Logback initialization warning mentions `FILE_JSON` or `/var/log/community`.

- [ ] **Step 7: Commit logging config**

```bash
git add backend/community-common/common-observability \
  backend/community-app/src/main/resources/logback-spring.xml \
  backend/community-gateway/src/main/resources/logback-spring.xml \
  backend/community-oss/src/main/resources/logback-spring.xml \
  backend/community-im-gateway/src/main/resources/logback-spring.xml \
  backend/community-im/im-core/src/main/resources/logback-spring.xml \
  backend/community-im/im-realtime/src/main/resources/logback-spring.xml \
  backend/community-app/src/test/java/com/nowcoder/community/infra/observability/RuntimeObservabilityIntegrationTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/infra/web/AuditLogFilterTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/infra/web/GlobalExceptionHandlerTest.java \
  backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/AccessLogWebFilterTest.java
git commit -m "feat: emit shared json logs to stdout"
```

## Task 6: Change Compose and EDOT Collector to Stdout Logs

**Files:**
- Modify: `deploy/compose.runtime.services.single.yml`
- Modify: `deploy/compose.runtime.services.cluster.yml`
- Modify: `deploy/compose.observability.yml`
- Modify: `deploy/compose.yml`
- Modify: `deploy/observability/edot-collector.yml`
- Modify: `deploy/tests/observability_otel_default.sh`
- Modify: `deploy/.env.single.example`
- Modify: `deploy/.env.cluster.example`

- [ ] **Step 1: Extend deploy test before changing compose**

Add these checks to `deploy/tests/observability_otel_default.sh` after the default config files are rendered:

```bash
if rg -n '/var/log/community|COMMUNITY_LOGGING_DIR|COMMUNITY_LOGGING_FILE_NAME|volume-log-export' "${single_config}" >/dev/null; then
  echo "expected single config to avoid file-volume application logs" >&2
  exit 1
fi

if rg -n '/var/log/community|COMMUNITY_LOGGING_DIR|COMMUNITY_LOGGING_FILE_NAME|volume-log-export' "${cluster_config}" >/dev/null; then
  echo "expected cluster config to avoid file-volume application logs" >&2
  exit 1
fi

if ! rg -n 'SPRING_PROFILES_ACTIVE[=: ].*json-logs|SPRING_PROFILES_ACTIVE=.*json-logs' "${single_config}" >/dev/null; then
  echo "expected single config to activate json-logs for backend stdout" >&2
  exit 1
fi

if ! rg -n 'OTEL_LOGS_COLLECTION[=: ]+"?stdout"?|OTEL_LOGS_COLLECTION=stdout' "${single_config}" >/dev/null; then
  echo "expected single config to mark stdout log collection" >&2
  exit 1
fi
```

- [ ] **Step 2: Run deploy test to verify failure**

Run:

```bash
bash deploy/tests/observability_otel_default.sh
```

Expected: FAIL because rendered compose still contains `/var/log/community`, `COMMUNITY_LOGGING_*`, and `volume-log-export`.

- [ ] **Step 3: Remove application log volume mounts**

In `deploy/compose.runtime.services.single.yml` and `deploy/compose.runtime.services.cluster.yml`, delete each base-service block:

```yaml
  volumes:
  - observability_logs:/var/log/community
```

In `deploy/compose.observability.yml`, replace:

```yaml
    volumes:
    - ./observability/edot-collector.yml:/etc/otelcol/config.yaml:ro
    - observability_logs:/var/log/community:ro
```

with:

```yaml
    volumes:
    - ./observability/edot-collector.yml:/etc/otelcol/config.yaml:ro
    - /var/lib/docker/containers:/var/lib/docker/containers:ro
```

In `deploy/compose.yml`, remove:

```yaml
  observability_logs:
    name: ${COMMUNITY_VOLUME_NAMESPACE}_observability_logs
```

- [ ] **Step 4: Replace backend logging env vars**

For every backend service in `deploy/compose.runtime.services.single.yml` and `deploy/compose.runtime.services.cluster.yml`, delete:

```yaml
    - COMMUNITY_LOGGING_DIR=/var/log/community
    - COMMUNITY_LOGGING_FILE_NAME=...
```

Change each `SPRING_PROFILES_ACTIVE` value so it contains `json-logs` and does not contain `volume-log-export`.

Single examples:

```yaml
- SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev},json-logs
- COMMUNITY_LOG_FORMAT=json
- DEPLOYMENT_ENVIRONMENT=${DEPLOYMENT_ENVIRONMENT:-local-compose}
- OTEL_LOGS_COLLECTION=stdout
```

Cluster examples with Redis cluster profile:

```yaml
- SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev},redis-cluster,json-logs
- COMMUNITY_LOG_FORMAT=json
- DEPLOYMENT_ENVIRONMENT=${DEPLOYMENT_ENVIRONMENT:-local-compose}
- OTEL_LOGS_COLLECTION=stdout
```

Keep `SERVICE_VERSION`, `OTEL_ENABLED`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_EXPORTER_OTLP_PROTOCOL`, and `OTEL_SERVICE_NAME`.

- [ ] **Step 5: Update EDOT collector log receiver**

Replace `deploy/observability/edot-collector.yml` log receiver with:

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318
  filelog/docker_stdout:
    include:
      - /var/lib/docker/containers/*/*.log
    start_at: end
    include_file_path: true
    operators:
      - type: container
```

Replace `transform/logs_bodymap` with:

```yaml
  transform/logs_bodymap:
    error_mode: ignore
    log_statements:
      - context: log
        statements:
          - set(body, ParseJSON(body)) where IsString(body) and IsMatch(body, "^\\s*\\{")
      - context: scope
        statements:
          - set(attributes["elastic.mapping.mode"], "bodymap")
  filter/backend_json_logs:
    error_mode: ignore
    logs:
      log_record:
        - 'not (IsMap(body) and body["service.name"] != nil)'
```

Change the logs pipeline to:

```yaml
    logs:
      receivers: [filelog/docker_stdout]
      processors: [memory_limiter, transform/logs_bodymap, filter/backend_json_logs, resource/community_service_namespace, batch]
      exporters: [elasticsearch/logs]
```

- [ ] **Step 6: Update env examples**

In `deploy/.env.single.example` and `deploy/.env.cluster.example`, replace the file-volume observability comment with:

```text
# Observability logs:
# - Backend application logs are structured JSON on stdout.
# - EDOT Collector reads Docker stdout logs and OTLP traces / metrics.
# - `SERVICE_VERSION` is also exported as OTel `service.version`.
```

Add:

```env
DEPLOYMENT_ENVIRONMENT=local-compose
COMMUNITY_LOG_FORMAT=json
OTEL_LOGS_COLLECTION=stdout
```

- [ ] **Step 7: Run deploy config test**

Run:

```bash
bash deploy/tests/observability_otel_default.sh
```

Expected: PASS.

- [ ] **Step 8: Validate collector config parses**

Run:

```bash
docker run --rm -v "$PWD/deploy/observability/edot-collector.yml:/etc/otelcol/config.yaml:ro" docker.elastic.co/elastic-agent/elastic-otel-collector:9.3.2 validate --config=/etc/otelcol/config.yaml
```

Expected: PASS. If the image does not support `validate`, run:

```bash
docker run --rm -v "$PWD/deploy/observability/edot-collector.yml:/etc/otelcol/config.yaml:ro" docker.elastic.co/elastic-agent/elastic-otel-collector:9.3.2 --config=/etc/otelcol/config.yaml --dry-run
```

Expected: process exits 0 after loading config.

- [ ] **Step 9: Commit deploy stdout collection**

```bash
git add deploy/compose.runtime.services.single.yml \
  deploy/compose.runtime.services.cluster.yml \
  deploy/compose.observability.yml \
  deploy/compose.yml \
  deploy/observability/edot-collector.yml \
  deploy/tests/observability_otel_default.sh \
  deploy/.env.single.example \
  deploy/.env.cluster.example
git commit -m "deploy: collect backend stdout logs through edot"
```

## Task 7: Update Kibana Assets and Handbook Docs

**Files:**
- Modify: `deploy/observability/kibana/README.md`
- Modify: `deploy/observability/kibana/saved-objects.ndjson`
- Modify: `docs/handbook/operations.md`
- Modify: `docs/handbook/local-development.md`
- Modify: `docs/handbook/system-design.md`
- Modify: `deploy/README.md`

- [ ] **Step 1: Update Kibana README**

In `deploy/observability/kibana/README.md`, replace the log source bullets with:

```markdown
- `logs-*` comes from structured JSON backend stdout collected by the EDOT Collector from Docker container logs.
- `traces-*` is populated by default when services are started through `deployment.sh`; use `OTEL_ENABLED=false` to keep the overlay but opt out of traces / metrics, or use `--no-observability` to disable the overlay.
- Use `trace.id` to pivot between logs and spans. The frontend-visible `traceId` is the OTel trace id for instrumented backend requests.
```

- [ ] **Step 2: Update saved object descriptions**

In `deploy/observability/kibana/saved-objects.ndjson`, replace:

```text
shared observability logs volume
```

with:

```text
backend JSON stdout collected by EDOT
```

Replace:

```text
observability compose path
```

with:

```text
stdout/OTLP observability path
```

Change the logs index-pattern name from:

```text
Community Observability Logs (Structured, Phase 1)
```

to:

```text
Community Observability Logs (JSON Stdout)
```

- [ ] **Step 3: Update handbook operations architecture**

In `docs/handbook/operations.md`, replace the current log flow diagram with:

```text
Backend SLF4J / Logback JSON stdout
  -> Docker container logs
  -> EDOT collector logs pipeline
  -> Elasticsearch logs-*
  -> Kibana

OTLP traces / metrics
  -> EDOT collector
  -> Elasticsearch traces-* / metrics-*
  -> Kibana
```

Replace the line that says logs still use the observability volume with:

```markdown
主要后端 deployable 默认启用业务无关运行态日志，包括 `community-app`、`community-oss`、`im-core`、`im-realtime`、`community-gateway` 和 `community-im-gateway`。日志通过共享 Logback 配置输出为 JSON stdout，由 EDOT Collector 从 Docker stdout 日志采集。运行态日志只记录启动摘要、阈值事件和慢请求事件，不记录请求 body、cookie、Authorization、SQL bind、Redis key、Kafka payload 或完整 object key。
```

Replace the troubleshooting bullet about shared volumes with:

```markdown
- backend 是否启动了 `json-logs` profile，`docker compose logs <service>` 是否能看到 JSON 行。
- EDOT collector 是否挂载 `/var/lib/docker/containers` 并成功启动 logs pipeline。
```

- [ ] **Step 4: Update local development and deploy README**

In `docs/handbook/local-development.md` and `deploy/README.md`, state:

```markdown
默认 observability overlay 会设置 `OTEL_ENABLED=true`，后端使用 OTel Java Agent 输出 traces / metrics 到 EDOT Collector，并通过 `json-logs` profile 输出 JSON stdout。关闭 overlay 时，后端仍会写 stdout，`docker compose logs` 仍可查看本地日志。
```

- [ ] **Step 5: Update system design**

In `docs/handbook/system-design.md`, replace the observability paragraph that mentions SLF4J/MDC plus volume collection with:

```markdown
业务无关运行态日志由共享 `community-common-observability` 提供，属于基础设施能力，不进入任何业务 domain 或 application 编排。后端服务通过 Spring Boot auto-configuration 接入，应用代码继续使用 SLF4J；Logback 统一输出 JSON stdout，EDOT Collector 从容器 stdout 采集日志，同时通过 OTLP 接收 traces / metrics。链路关联以 OpenTelemetry trace context 为准，`Result.traceId`、响应 `traceparent`、日志 `trace.id`、Kafka/outbox `traceparent` 和 `traces-*` 使用同一个 trace id。
```

- [ ] **Step 6: Run doc grep checks**

Run:

```bash
rg -n "observability volume|shared observability|/var/log/community|volume-log-export|COMMUNITY_LOGGING_DIR|COMMUNITY_LOGGING_FILE_NAME|filelog receiver" docs/handbook deploy/README.md deploy/observability/kibana
```

Expected: no matches in current handbook/deploy docs except historical specs under `docs/superpowers/specs`, which are not part of this command.

- [ ] **Step 7: Commit docs and Kibana assets**

```bash
git add deploy/observability/kibana/README.md \
  deploy/observability/kibana/saved-objects.ndjson \
  docs/handbook/operations.md \
  docs/handbook/local-development.md \
  docs/handbook/system-design.md \
  deploy/README.md
git commit -m "docs: document otel-first stdout observability"
```

## Task 8: End-to-End Verification

**Files:**
- No planned source edits.
- Use source edits from Tasks 1-7.

- [ ] **Step 1: Run common module tests**

Run:

```bash
cd backend
mvn test -pl :community-common-core,:community-common-web,:community-common-webflux,:community-common-security,:community-common-kafka,:community-common-outbox,:community-common-observability
```

Expected: PASS.

- [ ] **Step 2: Run affected deployable tests**

Run:

```bash
cd backend
mvn test -pl :community-app,:community-gateway,:community-oss,:community-im-gateway,:im-core,:im-realtime -Dtest='RuntimeObservabilityIntegrationTest,AuditLogFilterTest,GlobalExceptionHandlerTest,AccessLogWebFilterTest,ObservedObjectStoreTest,CommandConsumersLoggingTest,JdbcOutboxEventStoreTest,OutboxWorkerRetryTest,AfterCommitExecutorTest,Trace*Test,*Outbox*HandlerTest,CommandProducerTest'
```

Expected: PASS.

- [ ] **Step 3: Run architecture guardrails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: PASS. This change should stay in common infrastructure and deploy docs, not backend business layers.

- [ ] **Step 4: Run deploy tests**

Run:

```bash
bash deploy/tests/observability_otel_default.sh
bash deploy/tests/topology_single_cluster.sh
```

Expected: PASS.

- [ ] **Step 5: Render compose and check removed file-volume path**

Run:

```bash
./deploy/deployment.sh config --topology single --env-file deploy/.env.single.example > /tmp/community-single-otel.yml
./deploy/deployment.sh config --topology cluster --env-file deploy/.env.cluster.example > /tmp/community-cluster-otel.yml
rg -n '/var/log/community|COMMUNITY_LOGGING_DIR|COMMUNITY_LOGGING_FILE_NAME|volume-log-export|observability_logs' /tmp/community-single-otel.yml /tmp/community-cluster-otel.yml
```

Expected: `rg` exits with code 1 and prints no matches.

- [ ] **Step 6: Run local smoke test with observability**

Run:

```bash
./deploy/deployment.sh up --topology single --env-file deploy/.env.single.example -d
```

Expected: backend services and `community-observability-gateway-edot-collector` start.

Then run:

```bash
docker compose -p community -f deploy/compose.yml -f deploy/compose.runtime.services.single.yml -f deploy/compose.observability.yml logs --tail=20 community-app
```

Expected: recent `community-app` output contains JSON lines starting with `{` and fields `service.name`, `service.namespace`, `deployment.environment`, `level`, `logger`, and `message`.

- [ ] **Step 7: Verify one trace id pivots across logs and traces**

Send a request:

```bash
curl -sS -D /tmp/community-headers.txt http://127.0.0.1:12880/api/posts | tee /tmp/community-response.json
```

Extract a trace id from the response body or `traceparent` header:

```bash
TRACE_ID="$(jq -r '.traceId // empty' /tmp/community-response.json)"
if [ -z "${TRACE_ID}" ]; then TRACE_ID="$(awk 'tolower($1)=="traceparent:" {print $2}' /tmp/community-headers.txt | tr -d '\r' | cut -d- -f2)"; fi
printf '%s\n' "${TRACE_ID}"
```

Expected: prints a 32-character lowercase hex trace id.

Query Elasticsearch:

```bash
curl -sS 'http://127.0.0.1:12888/traces-*/_search' \
  -H 'Content-Type: application/json' \
  -d "{\"size\":1,\"query\":{\"term\":{\"trace.id\":\"${TRACE_ID}\"}}}" | jq '.hits.total'
curl -sS 'http://127.0.0.1:12888/logs-*/_search' \
  -H 'Content-Type: application/json' \
  -d "{\"size\":1,\"query\":{\"term\":{\"trace.id\":\"${TRACE_ID}\"}}}" | jq '.hits.total'
```

Expected: traces query returns at least one hit. Logs query returns at least one hit after a semantic, audit, exception, or threshold log is produced under the same request; if the initial read request produces no semantic log, trigger a write/audit request or lower the slow-request threshold for the smoke test.

- [ ] **Step 8: Commit verification-only fixes if any**

If verification required small config or test fixes, commit them:

```bash
git add <changed-files>
git commit -m "test: verify otel-first observability"
```

Use the real changed file list instead of staging unrelated files.
