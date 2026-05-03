# Community Complete Tracing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the observability overlay enable complete project-wide tracing by default and propagate trace context across HTTP, WebFlux, WebSocket, Kafka, outbox, jobs, and local async boundaries.

**Architecture:** Use the OpenTelemetry Java agent as the primary span producer and add repository-owned trace bridge code at technical boundaries. Keep tracing out of domain/application logic; common modules own reusable tracing helpers, while infrastructure adapters inject/restore context.

**Tech Stack:** Java 17, Spring Boot 3, Spring Web, Spring WebFlux, Spring Kafka, JDBC/H2/MySQL, Docker Compose, OpenTelemetry Java agent, EDOT Collector, Elastic/Kibana.

---

## File Structure

- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextSnapshot.java`
  - New immutable snapshot for carrying `traceId` and `traceparent` across local/durable boundaries.
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextScope.java`
  - New `AutoCloseable` scope that restores previous `TraceId`/MDC state.
- `backend/community-common/common-core/pom.xml`
  - Add AssertJ for the new focused common-core tests.
- `backend/community-common/common-core/src/test/java/com/nowcoder/community/common/trace/TraceContextSnapshotTest.java`
  - Tests capture, fallback generation, wrapping, and cleanup.
- `backend/community-common/common-kafka/pom.xml`
  - New common Kafka tracing module with Spring Kafka dependency.
- `backend/community-common/common-kafka/src/main/java/com/nowcoder/community/common/kafka/trace/TraceKafkaHeaders.java`
  - Kafka header inject/extract helpers.
- `backend/community-common/common-kafka/src/main/java/com/nowcoder/community/common/kafka/trace/TraceKafkaSender.java`
  - Small wrapper for `KafkaTemplate.send(ProducerRecord)`.
- `backend/community-common/common-kafka/src/main/java/com/nowcoder/community/common/kafka/trace/TraceRecordInterceptor.java`
  - Listener factory interceptor that restores trace before listener execution and clears it after record handling.
- `backend/community-common/common-kafka/src/test/java/com/nowcoder/community/common/kafka/trace/*Test.java`
  - Kafka header and interceptor tests.
- `backend/community-common/pom.xml`
  - Add `common-kafka` module.
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEvent.java`
  - Add `traceId` and `traceparent`.
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java`
  - Capture trace during enqueue and map trace columns.
- `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorker.java`
  - Restore outbox trace around handler dispatch and generate fallback for old rows.
- `deploy/mysql/community/010_schema_shared.sql`
  - Add trace columns to shared outbox table idempotently.
- `deploy/mysql/community/070_schema_im_core.sql`
  - Add trace columns to IM core outbox table idempotently.
- `backend/community-app/src/test/resources/schema.sql`
  - Add outbox trace columns for app tests.
- `backend/community-im/im-core/src/test/resources/schema.sql`
  - Add outbox trace columns for IM core tests.
- `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/tx/AfterCommitExecutor.java`
  - Capture trace at registration and restore at callback execution.
- `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/trace/TraceJobRunner.java`
  - New helper for scheduler/XXL job trace roots.
- `backend/community-app/src/main/java/**/infrastructure/job/*Handler.java`, `backend/community-app/src/main/java/**/infrastructure/job/*Job.java`, `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/PostScoreRefresher.java`, `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorkerScheduler.java`
  - Wrap job/scheduler entrypoints in `TraceJobRunner.run`.
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/CommandProducer.java`
  - Use `TraceKafkaSender` for command records.
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/KafkaConfig.java`
  - Register `TraceRecordInterceptor`.
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/KafkaConfig.java`
  - Register `TraceRecordInterceptor`.
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/outbox/ImKafkaOutboxHandler.java`
  - Use `TraceKafkaSender` for outbox event records.
- `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyKafkaOutboxHandler.java`
  - Use `TraceKafkaSender` for policy projection events.
- `backend/community-app/pom.xml`, `backend/community-im/im-core/pom.xml`, `backend/community-im/im-realtime/pom.xml`
  - Add `community-common-kafka` dependency where Kafka helpers are used.
- `deploy/deployment.sh`
  - Default `OTEL_ENABLED=true` only when `--observability` is used and caller has not explicitly set `OTEL_ENABLED`.
- `deploy/tests/observability_otel_default.sh`
  - New shell test for deployment config behavior.
- `docs/handbook/operations.md`, `docs/handbook/local-development.md`, `deploy/README.md`, `deploy/observability/kibana/README.md`
  - Update runbook and override instructions.

---

### Task 1: Add Core Trace Snapshot And Scope

**Files:**
- Modify: `backend/community-common/common-core/pom.xml`
- Create: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextSnapshot.java`
- Create: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextScope.java`
- Test: `backend/community-common/common-core/src/test/java/com/nowcoder/community/common/trace/TraceContextSnapshotTest.java`

- [ ] **Step 1: Write the failing tests**

Create `backend/community-common/common-core/src/test/java/com/nowcoder/community/common/trace/TraceContextSnapshotTest.java`:

```java
package com.nowcoder.community.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextSnapshotTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void fromInboundShouldPreferTraceparentAndNormalizeLegacyFallback() {
        TraceContextSnapshot fromTraceparent = TraceContextSnapshot.fromInbound(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        );

        assertThat(fromTraceparent.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(fromTraceparent.traceparent()).startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
        assertThat(fromTraceparent.recovered()).isFalse();

        TraceContextSnapshot fromLegacy = TraceContextSnapshot.fromInbound(
                "ABCDEFABCDEFABCDEFABCDEFABCDEFAB",
                null
        );

        assertThat(fromLegacy.traceId()).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
        assertThat(fromLegacy.traceparent()).startsWith("00-abcdefabcdefabcdefabcdefabcdefab-");
        assertThat(fromLegacy.recovered()).isFalse();
    }

    @Test
    void currentOrNewShouldCaptureCurrentTraceAndGenerateWhenMissing() {
        TraceContext.set("11111111111111111111111111111111");

        TraceContextSnapshot current = TraceContextSnapshot.currentOrNew();

        assertThat(current.traceId()).isEqualTo("11111111111111111111111111111111");
        assertThat(current.traceparent()).startsWith("00-11111111111111111111111111111111-");
        assertThat(current.recovered()).isFalse();

        TraceContext.clear();

        TraceContextSnapshot generated = TraceContextSnapshot.currentOrNew();

        assertThat(generated.traceId()).matches("[0-9a-f]{32}");
        assertThat(generated.traceparent()).startsWith("00-" + generated.traceId() + "-");
        assertThat(generated.recovered()).isTrue();
    }

    @Test
    void openShouldRestorePreviousTraceAndMdc() {
        TraceContext.set("11111111111111111111111111111111");
        TraceContextSnapshot snapshot = TraceContextSnapshot.fromStored(
                "22222222222222222222222222222222",
                "00-22222222222222222222222222222222-00f067aa0ba902b7-01"
        );

        try (TraceContextScope ignored = snapshot.open()) {
            assertThat(TraceId.get()).isEqualTo("22222222222222222222222222222222");
            assertThat(MDC.get("traceId")).isEqualTo("22222222222222222222222222222222");
        }

        assertThat(TraceId.get()).isEqualTo("11111111111111111111111111111111");
        assertThat(MDC.get("traceId")).isEqualTo("11111111111111111111111111111111");
    }

    @Test
    void wrapShouldRunWithSnapshotAndClearGeneratedTraceAfterwards() {
        TraceContextSnapshot snapshot = TraceContextSnapshot.fromStored(
                "33333333333333333333333333333333",
                null
        );
        AtomicReference<String> seen = new AtomicReference<>();

        snapshot.wrap(() -> seen.set(TraceId.get())).run();

        assertThat(seen.get()).isEqualTo("33333333333333333333333333333333");
        assertThat(TraceId.get()).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }
}
```

- [ ] **Step 2: Add the test dependency**

Add AssertJ to `backend/community-common/common-core/pom.xml` test dependencies:

```xml
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 3: Run the tests to verify RED**

Run:

```bash
cd backend && mvn -q -pl community-common/common-core -Dtest=TraceContextSnapshotTest test
```

Expected: FAIL with compilation errors for missing `TraceContextSnapshot` and `TraceContextScope`.

- [ ] **Step 4: Implement the snapshot and scope**

Create `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextSnapshot.java`:

```java
package com.nowcoder.community.common.trace;

/**
 * Immutable trace context captured at a technical boundary.
 */
public record TraceContextSnapshot(String traceId, String traceparent, boolean recovered) {

    public TraceContextSnapshot {
        traceId = TraceIdCodec.normalizeTraceId(traceId);
        if (traceId == null) {
            traceId = TraceIdCodec.generateTraceId();
            recovered = true;
        }
        String extracted = TraceIdCodec.extractTraceIdFromTraceparent(traceparent);
        if (!traceId.equals(extracted)) {
            traceparent = TraceIdCodec.buildTraceparent(traceId);
        } else {
            traceparent = traceparent.trim();
        }
    }

    public static TraceContextSnapshot fromInbound(String traceIdHeader, String traceparentHeader) {
        String resolved = TraceIdCodec.resolveTraceId(traceIdHeader, traceparentHeader);
        String extracted = TraceIdCodec.extractTraceIdFromTraceparent(traceparentHeader);
        String parent = resolved.equals(extracted) ? traceparentHeader : TraceIdCodec.buildTraceparent(resolved);
        return new TraceContextSnapshot(resolved, parent, false);
    }

    public static TraceContextSnapshot fromStored(String traceId, String traceparent) {
        String normalized = TraceIdCodec.normalizeTraceId(traceId);
        boolean recovered = normalized == null;
        return new TraceContextSnapshot(normalized, traceparent, recovered);
    }

    public static TraceContextSnapshot currentOrNew() {
        String current = TraceIdCodec.normalizeTraceId(TraceId.get());
        boolean recovered = current == null;
        return new TraceContextSnapshot(current, null, recovered);
    }

    public TraceContextScope open() {
        return TraceContextScope.open(this);
    }

    public Runnable wrap(Runnable action) {
        if (action == null) {
            return () -> {
            };
        }
        return () -> {
            try (TraceContextScope ignored = open()) {
                action.run();
            }
        };
    }
}
```

Create `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextScope.java`:

```java
package com.nowcoder.community.common.trace;

/**
 * Restores the previous thread trace context after a boundary operation.
 */
public final class TraceContextScope implements AutoCloseable {

    private final String previousTraceId;
    private boolean closed;

    private TraceContextScope(String previousTraceId) {
        this.previousTraceId = TraceIdCodec.normalizeTraceId(previousTraceId);
    }

    static TraceContextScope open(TraceContextSnapshot snapshot) {
        String previous = TraceId.get();
        TraceContextScope scope = new TraceContextScope(previous);
        if (snapshot != null) {
            TraceContext.set(snapshot.traceId());
        }
        return scope;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        TraceContext.clear();
        if (previousTraceId != null) {
            TraceContext.set(previousTraceId);
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify GREEN**

Run:

```bash
cd backend && mvn -q -pl community-common/common-core -Dtest=TraceContextSnapshotTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-common/common-core/pom.xml \
        backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextSnapshot.java \
        backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextScope.java \
        backend/community-common/common-core/src/test/java/com/nowcoder/community/common/trace/TraceContextSnapshotTest.java
git commit -m "feat: add trace context snapshot"
```

---

### Task 2: Add Common Kafka Trace Propagation Module

**Files:**
- Modify: `backend/community-common/pom.xml`
- Create: `backend/community-common/common-kafka/pom.xml`
- Create: `backend/community-common/common-kafka/src/main/java/com/nowcoder/community/common/kafka/trace/TraceKafkaHeaders.java`
- Create: `backend/community-common/common-kafka/src/main/java/com/nowcoder/community/common/kafka/trace/TraceKafkaSender.java`
- Create: `backend/community-common/common-kafka/src/main/java/com/nowcoder/community/common/kafka/trace/TraceRecordInterceptor.java`
- Test: `backend/community-common/common-kafka/src/test/java/com/nowcoder/community/common/kafka/trace/TraceKafkaHeadersTest.java`
- Test: `backend/community-common/common-kafka/src/test/java/com/nowcoder/community/common/kafka/trace/TraceRecordInterceptorTest.java`

- [ ] **Step 1: Write the failing tests**

Create `backend/community-common/common-kafka/src/test/java/com/nowcoder/community/common/kafka/trace/TraceKafkaHeadersTest.java`:

```java
package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import com.nowcoder.community.common.trace.TraceHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceKafkaHeadersTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void injectShouldWriteTraceHeadersToProducerRecord() {
        TraceContext.set("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        ProducerRecord<String, Object> record = new ProducerRecord<>("topic-a", "key-1", "value-1");

        TraceKafkaHeaders.inject(record.headers(), TraceContextSnapshot.currentOrNew());

        assertThat(TraceKafkaHeaders.headerValue(record.headers(), TraceHeaders.HEADER_TRACE_ID))
                .isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(TraceKafkaHeaders.headerValue(record.headers(), TraceHeaders.HEADER_TRACEPARENT))
                .startsWith("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-");
    }

    @Test
    void extractShouldPreferTraceparentAndFallbackToLegacyHeader() {
        ProducerRecord<String, Object> record = new ProducerRecord<>("topic-a", "key-1", "value-1");
        record.headers().add(TraceHeaders.HEADER_TRACE_ID, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        record.headers().add(TraceHeaders.HEADER_TRACEPARENT, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        TraceContextSnapshot snapshot = TraceKafkaHeaders.extract(record.headers());

        assertThat(snapshot.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(snapshot.recovered()).isFalse();
    }
}
```

Create `backend/community-common/common-kafka/src/test/java/com/nowcoder/community/common/kafka/trace/TraceRecordInterceptorTest.java`:

```java
package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TraceRecordInterceptorTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void interceptorShouldRestoreTraceBeforeListenerAndClearAfterRecord() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(TraceHeaders.HEADER_TRACE_ID, "cccccccccccccccccccccccccccccccc".getBytes(StandardCharsets.UTF_8));
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "topic-a",
                0,
                1L,
                0L,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                0L,
                0,
                "key",
                "value",
                headers
        );
        TraceRecordInterceptor interceptor = new TraceRecordInterceptor();

        ConsumerRecord<Object, Object> intercepted = interceptor.intercept(record, null);

        assertThat(intercepted).isSameAs(record);
        assertThat(TraceId.get()).isEqualTo("cccccccccccccccccccccccccccccccc");

        interceptor.afterRecord(record, null);

        assertThat(TraceId.get()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
cd backend && mvn -q -pl community-common/common-kafka -Dtest=TraceKafkaHeadersTest,TraceRecordInterceptorTest test
```

Expected: FAIL because module `community-common-kafka` does not exist.

- [ ] **Step 3: Add the module and implementation**

Modify `backend/community-common/pom.xml` by adding the module after `common-spring`:

```xml
        <module>common-kafka</module>
```

Create `backend/community-common/common-kafka/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.nowcoder.community</groupId>
        <artifactId>community-common</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>community-common-kafka</artifactId>
    <name>community-common-kafka</name>
    <description>Business-agnostic Kafka tracing utilities</description>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>community-common-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Create `TraceKafkaHeaders.java`:

```java
package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.TraceContextSnapshot;
import com.nowcoder.community.common.trace.TraceHeaders;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;

public final class TraceKafkaHeaders {

    private TraceKafkaHeaders() {
    }

    public static void inject(Headers headers, TraceContextSnapshot snapshot) {
        if (headers == null || snapshot == null) {
            return;
        }
        put(headers, TraceHeaders.HEADER_TRACE_ID, snapshot.traceId());
        put(headers, TraceHeaders.HEADER_TRACEPARENT, snapshot.traceparent());
    }

    public static TraceContextSnapshot extract(Headers headers) {
        return TraceContextSnapshot.fromInbound(
                headerValue(headers, TraceHeaders.HEADER_TRACE_ID),
                headerValue(headers, TraceHeaders.HEADER_TRACEPARENT)
        );
    }

    public static String headerValue(Headers headers, String name) {
        if (headers == null || name == null || name.isBlank()) {
            return null;
        }
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private static void put(Headers headers, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        headers.remove(name);
        headers.add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
```

Create `TraceKafkaSender.java`:

```java
package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.TraceContextSnapshot;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

public final class TraceKafkaSender {

    private TraceKafkaSender() {
    }

    public static <K, V> CompletableFuture<SendResult<K, V>> send(
            KafkaTemplate<K, V> kafkaTemplate,
            String topic,
            K key,
            V value
    ) {
        ProducerRecord<K, V> record = new ProducerRecord<>(topic, key, value);
        TraceKafkaHeaders.inject(record.headers(), TraceContextSnapshot.currentOrNew());
        return kafkaTemplate.send(record);
    }
}
```

Create `TraceRecordInterceptor.java`:

```java
package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.TraceContextScope;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

public class TraceRecordInterceptor implements RecordInterceptor<Object, Object> {

    private final ThreadLocal<TraceContextScope> currentScope = new ThreadLocal<>();

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        closeCurrentScope();
        TraceContextSnapshot snapshot = TraceKafkaHeaders.extract(record == null ? null : record.headers());
        currentScope.set(snapshot.open());
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        closeCurrentScope();
    }

    private void closeCurrentScope() {
        TraceContextScope scope = currentScope.get();
        currentScope.remove();
        if (scope != null) {
            scope.close();
        }
    }
}
```

- [ ] **Step 4: Run tests to verify GREEN**

Run:

```bash
cd backend && mvn -q -pl community-common/common-kafka -Dtest=TraceKafkaHeadersTest,TraceRecordInterceptorTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-common/pom.xml \
        backend/community-common/common-kafka
git commit -m "feat: add kafka trace propagation helpers"
```

---

### Task 3: Persist And Restore Outbox Trace Context

**Files:**
- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxEvent.java`
- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStore.java`
- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorker.java`
- Modify: `deploy/mysql/community/010_schema_shared.sql`
- Modify: `deploy/mysql/community/070_schema_im_core.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Modify: `backend/community-im/im-core/src/test/resources/schema.sql`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/infra/outbox/JdbcOutboxEventStoreTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/infra/outbox/OutboxWorkerRetryTest.java`

- [ ] **Step 1: Write failing outbox store tests**

In `JdbcOutboxEventStoreTest.enqueueAndClaimShouldWork`, set a trace before enqueue and assert selected outbox fields:

```java
com.nowcoder.community.common.trace.TraceContext.set("dddddddddddddddddddddddddddddddd");

boolean inserted = store.enqueue(
        "e-1:points",
        "projection.points",
        "1",
        "{\"userId\":1,\"delta\":10}"
);
assertThat(inserted).isTrue();

List<OutboxEvent> due = store.findDuePending(10, now);
assertThat(due).hasSize(1);

OutboxEvent ev = due.get(0);
assertThat(ev.traceId()).isEqualTo("dddddddddddddddddddddddddddddddd");
assertThat(ev.traceparent()).startsWith("00-dddddddddddddddddddddddddddddddd-");
```

Also add `TraceContext.clear();` in a `finally` block before `db.shutdown();`.

- [ ] **Step 2: Write failing worker restore test**

Add this test to `OutboxWorkerRetryTest`:

```java
@Test
void workerShouldRestoreOutboxTraceDuringHandlerAndClearAfterwards() {
    Instant now = Instant.parse("2026-03-14T00:00:00Z");
    JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
    OutboxProperties properties = enabledProperties();
    UUID outboxId = UUID.fromString("01965429-b34a-7000-8000-000000000003");
    OutboxEvent event = new OutboxEvent(
            outboxId,
            "e-trace:points",
            "projection.points",
            "1",
            "{}",
            OutboxEventStatus.PENDING,
            0,
            null,
            null,
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
            "00-eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee-00f067aa0ba902b7-01"
    );
    AtomicReference<String> seenTrace = new AtomicReference<>();
    OutboxHandler handler = new OutboxHandler() {
        @Override
        public String topic() {
            return "projection.points";
        }

        @Override
        public void handle(OutboxEvent ignored) {
            seenTrace.set(com.nowcoder.community.common.trace.TraceId.get());
        }
    };

    when(store.recoverExpiredLeases(now)).thenReturn(0);
    when(store.findDuePending(properties.getBatchSize(), now)).thenReturn(java.util.List.of(event));
    when(store.tryClaimProcessing(eq(outboxId), any(), eq(now))).thenReturn(true);

    OutboxWorker worker = new OutboxWorker(store, Map.of(handler.topic(), handler), properties, Clock.fixed(now, ZoneOffset.UTC));

    int processed = worker.pollOnce();

    assertThat(processed).isEqualTo(1);
    assertThat(seenTrace.get()).isEqualTo("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
    assertThat(com.nowcoder.community.common.trace.TraceId.get()).isNull();
    verify(store).markSucceeded(outboxId, now);
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=JdbcOutboxEventStoreTest,OutboxWorkerRetryTest test
```

Expected: FAIL because `OutboxEvent.traceId()` / `traceparent()` do not exist and the outbox schema lacks trace columns.

- [ ] **Step 4: Add trace fields and persistence**

Change `OutboxEvent` to:

```java
package com.nowcoder.community.common.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * A row from {@code community.outbox_event}.
 */
public record OutboxEvent(
        UUID id,
        String eventId,
        String topic,
        String eventKey,
        String payload,
        String status,
        int retryCount,
        Instant nextRetryAt,
        String lastError,
        String traceId,
        String traceparent
) {
}
```

Update `JdbcOutboxEventStore.enqueue` to capture current trace:

```java
TraceContextSnapshot trace = TraceContextSnapshot.currentOrNew();
jdbcTemplate.update(
        "insert into outbox_event(id, event_id, topic, event_key, payload, status, retry_count, next_retry_at, last_error, trace_id, traceparent) " +
                "values (?, ?, ?, ?, ?, ?, 0, null, null, ?, ?)",
        BinaryUuidCodec.toBytes(id),
        eid,
        t,
        k,
        p,
        OutboxEventStatus.PENDING,
        trace.traceId(),
        trace.traceparent()
);
```

Update the `findDuePending` select list:

```java
"select id, event_id, topic, event_key, payload, status, retry_count, next_retry_at, last_error, trace_id, traceparent " +
```

Update the row mapper constructor arguments:

```java
rs.getString("last_error"),
rs.getString("trace_id"),
rs.getString("traceparent")
```

Add imports to `JdbcOutboxEventStore`:

```java
import com.nowcoder.community.common.trace.TraceContextSnapshot;
```

Update `OutboxWorker` handler dispatch:

```java
try (var ignored = com.nowcoder.community.common.trace.TraceContextSnapshot
        .fromStored(event.traceId(), event.traceparent())
        .open()) {
    handler.handle(event);
    store.markSucceeded(event.id(), now);
} catch (RuntimeException e) {
    handleFailure(event, now, e);
}
```

- [ ] **Step 5: Add SQL columns**

In every `create table if not exists outbox_event` block in these files, add after `last_error`:

```sql
  trace_id varchar(32) null,
  traceparent varchar(128) null,
```

For MySQL deploy scripts `deploy/mysql/community/010_schema_shared.sql` and `deploy/mysql/community/070_schema_im_core.sql`, also add idempotent alters after the table definition:

```sql
set @col_outbox_trace_id := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'outbox_event'
    and column_name = 'trace_id'
);
set @sql := if(@col_outbox_trace_id = 0, 'alter table outbox_event add column trace_id varchar(32) null after last_error', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_outbox_traceparent := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'outbox_event'
    and column_name = 'traceparent'
);
set @sql := if(@col_outbox_traceparent = 0, 'alter table outbox_event add column traceparent varchar(128) null after trace_id', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
```

- [ ] **Step 6: Update existing test constructors**

Every direct `new OutboxEvent(...)` in tests must add the final two trace arguments. For tests that do not care about tracing, use:

```java
null,
null
```

- [ ] **Step 7: Run tests to verify GREEN**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=JdbcOutboxEventStoreTest,OutboxWorkerRetryTest,PostOutboxEnqueuerTest,PostOutboxHandlerTest,ImPolicyKafkaOutboxHandlerTest test
```

Run:

```bash
cd backend && mvn -q -pl community-im/im-core -Dtest=ImKafkaOutboxHandlerTest,PrivateMessageServiceTest,RoomMessageServiceTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox \
        backend/community-app/src/test/java/com/nowcoder/community/infra/outbox \
        backend/community-app/src/test/resources/schema.sql \
        backend/community-im/im-core/src/test/resources/schema.sql \
        deploy/mysql/community/010_schema_shared.sql \
        deploy/mysql/community/070_schema_im_core.sql \
        backend/community-app/src/test/java \
        backend/community-im/im-core/src/test/java
git commit -m "feat: persist trace context in outbox"
```

---

### Task 4: Wire Kafka Trace Helpers Into IM And App Adapters

**Files:**
- Modify: `backend/community-app/pom.xml`
- Modify: `backend/community-im/im-core/pom.xml`
- Modify: `backend/community-im/im-realtime/pom.xml`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/CommandProducer.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/KafkaConfig.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/KafkaConfig.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/outbox/ImKafkaOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyKafkaOutboxHandler.java`
- Test: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/kafka/CommandProducerTest.java`
- Test: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/outbox/ImKafkaOutboxHandlerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicyKafkaOutboxHandlerTest.java`

- [ ] **Step 1: Write failing producer test**

Create `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/kafka/CommandProducerTest.java`:

```java
package com.nowcoder.community.im.realtime.kafka;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandProducerTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void sendPrivateTextShouldAttachTraceHeaders() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        TraceContext.set("abababababababababababababababab");
        CommandProducer producer = new CommandProducer(kafkaTemplate);

        producer.sendPrivateText(new SendPrivateTextCommand(
                "req-1",
                "client-1",
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "conv-1",
                "hello",
                1L
        ));

        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(new String(record.headers().lastHeader(TraceHeaders.HEADER_TRACE_ID).value(), StandardCharsets.UTF_8))
                .isEqualTo("abababababababababababababababab");
        assertThat(new String(record.headers().lastHeader(TraceHeaders.HEADER_TRACEPARENT).value(), StandardCharsets.UTF_8))
                .startsWith("00-abababababababababababababababab-");
    }
}
```

Adjust constructor arguments if `SendPrivateTextCommand` differs; keep the assertion shape unchanged.

- [ ] **Step 2: Run producer test to verify RED**

Run:

```bash
cd backend && mvn -q -pl community-im/im-realtime -Dtest=CommandProducerTest test
```

Expected: FAIL because `CommandProducer` still calls `kafkaTemplate.send(topic, key, value)`.

- [ ] **Step 3: Add `community-common-kafka` dependencies**

Add this dependency to `backend/community-app/pom.xml`, `backend/community-im/im-core/pom.xml`, and `backend/community-im/im-realtime/pom.xml`:

```xml
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>community-common-kafka</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 4: Update producer and outbox sends**

In `CommandProducer`, replace both `kafkaTemplate.send(topic, key, cmd)` calls:

```java
return TraceKafkaSender.send(kafkaTemplate, ImTopics.COMMAND_PRIVATE_TEXT, cmd.conversationId(), cmd);
```

```java
return TraceKafkaSender.send(kafkaTemplate, ImTopics.COMMAND_ROOM_TEXT, String.valueOf(cmd.roomId()), cmd);
```

Add import:

```java
import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
```

In `ImKafkaOutboxHandler`, replace:

```java
kafkaTemplate.send(topic, event.eventKey(), payload).join();
```

with:

```java
TraceKafkaSender.send(kafkaTemplate, topic, event.eventKey(), payload).join();
```

In `ImPolicyKafkaOutboxHandler`, replace:

```java
kafkaTemplate.send(topic, key, value).join();
```

with:

```java
TraceKafkaSender.send(kafkaTemplate, topic, key, value).join();
```

- [ ] **Step 5: Register listener trace interceptors**

In `im-core` `KafkaConfig.kafkaListenerContainerFactory`, add after `factory.setCommonErrorHandler(errorHandler);`:

```java
factory.setRecordInterceptor(new TraceRecordInterceptor());
```

In `im-realtime` `KafkaConfig.kafkaListenerContainerFactory`, add before `return factory;`:

```java
factory.setRecordInterceptor(new TraceRecordInterceptor());
```

Add import in both files:

```java
import com.nowcoder.community.common.kafka.trace.TraceRecordInterceptor;
```

- [ ] **Step 6: Update existing outbox handler tests**

In `ImKafkaOutboxHandlerTest` and `ImPolicyKafkaOutboxHandlerTest`, update mocks/verifications from:

```java
when(kafkaTemplate.send(eq(topic), eq(key), any(Payload.class)))
verify(kafkaTemplate).send(eq(topic), eq(key), payloadCaptor.capture())
```

to:

```java
when(kafkaTemplate.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
        .thenReturn(completedSend());
verify(kafkaTemplate).send(any(org.apache.kafka.clients.producer.ProducerRecord.class));
```

For payload assertions, capture `ProducerRecord<String, Object>` and assert `record.value()`:

```java
ArgumentCaptor<org.apache.kafka.clients.producer.ProducerRecord<String, Object>> recordCaptor =
        ArgumentCaptor.forClass(org.apache.kafka.clients.producer.ProducerRecord.class);
verify(kafkaTemplate).send(recordCaptor.capture());
assertThat(recordCaptor.getValue().topic()).isEqualTo(ImTopics.EVENT_PRIVATE_PERSISTED);
assertThat(recordCaptor.getValue().key()).isEqualTo("conv-1");
assertThat(recordCaptor.getValue().value()).isInstanceOf(PrivateMessagePersistedEvent.class);
```

- [ ] **Step 7: Run tests to verify GREEN**

Run:

```bash
cd backend && mvn -q -pl community-im/im-realtime -Dtest=CommandProducerTest,ImRealtimeWebSocketIntegrationTest test
```

Run:

```bash
cd backend && mvn -q -pl community-im/im-core -Dtest=ImKafkaOutboxHandlerTest,CommandConsumersLoggingTest,ImCoreKafkaIntegrationTest test
```

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=ImPolicyKafkaOutboxHandlerTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/community-app/pom.xml \
        backend/community-im/im-core/pom.xml \
        backend/community-im/im-realtime/pom.xml \
        backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka \
        backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/kafka \
        backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka \
        backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/outbox \
        backend/community-im/im-core/src/test/java \
        backend/community-app/src/main/java/com/nowcoder/community/im/projection \
        backend/community-app/src/test/java/com/nowcoder/community/im/projection
git commit -m "feat: propagate trace through kafka adapters"
```

---

### Task 5: Preserve Trace Across After-Commit And Job Boundaries

**Files:**
- Modify: `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/tx/AfterCommitExecutor.java`
- Create: `backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/trace/TraceJobRunner.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/infra/tx/AfterCommitExecutorTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/infra/job/TraceJobRunnerTest.java`
- Modify: `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorkerScheduler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/PostScoreRefresher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/job/SearchReindexHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/infrastructure/job/MarketWalletActionRecoveryHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/infrastructure/job/MarketWalletActionProcessorHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/infrastructure/job/MarketOrderAutoConfirmHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/job/RefreshTokenCleanupJob.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/job/PendingRegistrationUserCleanupJob.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/job/PendingRegistrationUserCleanupHandler.java`

- [ ] **Step 1: Write failing after-commit trace test**

Add this test to `AfterCommitExecutorTest`:

```java
@Test
void runAfterCommit_shouldCaptureTraceAtRegistrationAndRestorePreviousAfterCallback() {
    TransactionSynchronizationManager.initSynchronization();
    try {
        com.nowcoder.community.common.trace.TraceContext.set("11111111111111111111111111111111");
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();

        AfterCommitExecutor.runAfterCommit(() -> seen.set(com.nowcoder.community.common.trace.TraceId.get()));

        com.nowcoder.community.common.trace.TraceContext.set("22222222222222222222222222222222");
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        assertThat(seen.get()).isEqualTo("11111111111111111111111111111111");
        assertThat(com.nowcoder.community.common.trace.TraceId.get()).isEqualTo("22222222222222222222222222222222");
    } finally {
        TransactionSynchronizationManager.clearSynchronization();
        com.nowcoder.community.common.trace.TraceContext.clear();
    }
}
```

- [ ] **Step 2: Write failing job runner test**

Create `backend/community-app/src/test/java/com/nowcoder/community/infra/job/TraceJobRunnerTest.java`:

```java
package com.nowcoder.community.infra.job;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceJobRunnerTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void runShouldCreateTraceWhenJobHasNoUpstreamContextAndClearAfterwards() {
        AtomicReference<String> seen = new AtomicReference<>();

        com.nowcoder.community.common.trace.TraceJobRunner.run("test-job", () -> seen.set(TraceId.get()));

        assertThat(seen.get()).matches("[0-9a-f]{32}");
        assertThat(TraceId.get()).isNull();
    }

    @Test
    void runShouldReuseCurrentTraceAndRestoreItAfterwards() {
        TraceContext.set("99999999999999999999999999999999");
        AtomicReference<String> seen = new AtomicReference<>();

        com.nowcoder.community.common.trace.TraceJobRunner.run("test-job", () -> seen.set(TraceId.get()));

        assertThat(seen.get()).isEqualTo("99999999999999999999999999999999");
        assertThat(TraceId.get()).isEqualTo("99999999999999999999999999999999");
    }
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=AfterCommitExecutorTest,TraceJobRunnerTest test
```

Expected: FAIL because after-commit does not capture trace and `TraceJobRunner` does not exist.

- [ ] **Step 4: Implement trace-aware after-commit and job runner**

Modify `AfterCommitExecutor.runAfterCommit`:

```java
public static void runAfterCommit(Runnable action) {
    if (action == null) {
        return;
    }

    Runnable tracedAction = com.nowcoder.community.common.trace.TraceContextSnapshot.currentOrNew().wrap(action);

    if (TransactionSynchronizationManager.isActualTransactionActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tracedAction.run();
            }
        });
        return;
    }

    tracedAction.run();
}
```

Create `TraceJobRunner.java`:

```java
package com.nowcoder.community.common.trace;

public final class TraceJobRunner {

    private TraceJobRunner() {
    }

    public static void run(String jobName, Runnable action) {
        if (action == null) {
            return;
        }
        TraceContextSnapshot.currentOrNew().wrap(action).run();
    }
}
```

- [ ] **Step 5: Wrap scheduler and job entrypoints**

For each job/scheduler method, wrap the existing body with:

```java
TraceJobRunner.run(JOB_NAME, () -> {
    // existing method body
});
```

Use these exact names:

- `OutboxWorkerScheduler.poll`: `"outbox-worker"`
- `PostScoreRefresher.refresh`: `"post-score-refresher"`
- `SearchReindexHandler.reindex`: existing `JOB_NAME`
- `MarketWalletActionRecoveryHandler.recover`: existing `JOB_NAME`
- `MarketWalletActionProcessorHandler.process`: existing `JOB_NAME`
- `MarketOrderAutoConfirmHandler.autoConfirm`: existing `JOB_NAME`
- `RefreshTokenCleanupJob.cleanup`: `"refresh-token-cleanup"`
- `PendingRegistrationUserCleanupJob.cleanup`: `"pending-registration-user-cleanup"`
- `PendingRegistrationUserCleanupHandler.cleanup`: existing `JOB_NAME`

Add import:

```java
import com.nowcoder.community.common.trace.TraceJobRunner;
```

If a method returns a value, keep the existing return outside only when the method signature requires it; current target job methods are void.

- [ ] **Step 6: Run focused job tests**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=AfterCommitExecutorTest,TraceJobRunnerTest,OutboxWorkerSchedulerTest,SearchReindexHandlerTest,MarketOrderAutoConfirmHandlerTest,PendingRegistrationUserCleanupHandlerTest,RefreshTokenCleanupJobTest,PendingRegistrationUserCleanupJobTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/tx/AfterCommitExecutor.java \
        backend/community-common/common-spring/src/main/java/com/nowcoder/community/common/trace/TraceJobRunner.java \
        backend/community-app/src/test/java/com/nowcoder/community/infra/tx/AfterCommitExecutorTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/infra/job/TraceJobRunnerTest.java \
        backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorkerScheduler.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/PostScoreRefresher.java \
        backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/job/SearchReindexHandler.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/infrastructure/job \
        backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/job \
        backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/job
git commit -m "feat: preserve trace across async jobs"
```

---

### Task 6: Enable OTel By Default Under Observability Overlay

**Files:**
- Modify: `deploy/deployment.sh`
- Create: `deploy/tests/observability_otel_default.sh`

- [ ] **Step 1: Write failing deployment behavior test**

Create `deploy/tests/observability_otel_default.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${repo_root}"

single_config="/tmp/community-observability-single.yml"
override_config="/tmp/community-observability-single-override.yml"

env -u OTEL_ENABLED ./deploy/deployment.sh config --topology single --observability --env-file deploy/.env.single.example >"${single_config}"

if ! rg -n 'OTEL_ENABLED[=: ]+true|OTEL_ENABLED=true' "${single_config}" >/dev/null; then
  echo "expected observability config to enable OTEL_ENABLED=true" >&2
  exit 1
fi

OTEL_ENABLED=false ./deploy/deployment.sh config --topology single --observability --env-file deploy/.env.single.example >"${override_config}"

if ! rg -n 'OTEL_ENABLED[=: ]+false|OTEL_ENABLED=false' "${override_config}" >/dev/null; then
  echo "expected explicit OTEL_ENABLED=false override to be preserved" >&2
  exit 1
fi
```

Make it executable:

```bash
chmod +x deploy/tests/observability_otel_default.sh
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
./deploy/tests/observability_otel_default.sh
```

Expected: FAIL because `--observability` still renders `OTEL_ENABLED=false` when the caller has not set it.

- [ ] **Step 3: Implement deployment default**

In `deploy/deployment.sh`, after observability compose files are appended and before `COMPOSE_CMD=(docker compose ...)`, add:

```bash
if [ "${ELASTIC}" -eq 1 ] && [ -z "${OTEL_ENABLED+x}" ]; then
  export OTEL_ENABLED=true
fi
```

The block must be placed after:

```bash
if [ "${ELASTIC}" -eq 1 ]; then
  COMPOSE_FILES+=(deploy/compose.observability.yml)
fi
```

- [ ] **Step 4: Run deployment tests to verify GREEN**

Run:

```bash
bash -n deploy/deployment.sh deploy/tests/observability_otel_default.sh
./deploy/tests/observability_otel_default.sh
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add deploy/deployment.sh deploy/tests/observability_otel_default.sh
git commit -m "feat: enable otel with observability overlay"
```

---

### Task 7: Update Runbooks And Kibana Notes

**Files:**
- Modify: `docs/handbook/operations.md`
- Modify: `docs/handbook/local-development.md`
- Modify: `deploy/README.md`
- Modify: `deploy/observability/kibana/README.md`
- Modify: `deploy/observability/kibana/saved-objects.ndjson`

- [ ] **Step 1: Write docs diff expectations**

Before editing, run:

```bash
rg -n "默认 `OTEL_ENABLED=false`|OTEL_ENABLED=false|traces-\\* only becomes useful|--observability" docs/handbook/operations.md docs/handbook/local-development.md deploy/README.md deploy/observability/kibana/README.md
```

Expected: Existing docs still describe OTel as manually enabled even with observability.

- [ ] **Step 2: Update operations runbook wording**

In `docs/handbook/operations.md`, replace the `traces / metrics` bullet list with:

```markdown
traces / metrics：

- 继续通过 OTLP -> EDOT collector -> Elastic。
- 普通启动默认 `OTEL_ENABLED=false`。
- 使用 `--observability` 时，`deployment.sh` 默认设置 `OTEL_ENABLED=true`，后端服务会加载 OTel Java agent。
- 如需在 observability overlay 下临时关闭 tracing，使用 `OTEL_ENABLED=false ./deploy/deployment.sh up --topology single --observability`。
```

Add this under the log lookup guidance:

```markdown
链路排障时：

- `trace.id` / `trace_id` 用于技术链路串联。
- `requestId`、事件 id、幂等 key 用于业务重放和消息确认，不作为 trace parent。
- 对 outbox 或 job 发起的链路，如果没有上游请求，系统会生成 job/outbox 处理 trace。
```

- [ ] **Step 3: Update local development and deploy README**

In `docs/handbook/local-development.md` and `deploy/README.md`, add after the `--observability` examples:

```markdown
带 `--observability` 时，后端服务默认开启 OTel tracing。普通启动不启用 OTel。需要关闭观测 overlay 下的 tracing 时，在命令前显式设置：

```bash
OTEL_ENABLED=false ./deploy/deployment.sh up --topology single --observability
```
```

- [ ] **Step 4: Update Kibana notes**

In `deploy/observability/kibana/README.md`, replace:

```markdown
- `traces-*` only becomes useful when `OTEL_ENABLED=true` and application spans are actually exported
```

with:

```markdown
- `traces-*` is populated by default when services are started through `deployment.sh ... --observability`; use `OTEL_ENABLED=false` to opt out
- Use `trace.id` / `trace_id` to pivot between logs and spans; use business `requestId` only for idempotency or message acknowledgement questions
```

- [ ] **Step 5: Add a simple Kibana search object for trace correlation**

Append a saved search entry to `deploy/observability/kibana/saved-objects.ndjson` with columns for both logs and traces:

```json
{"id":"community-observability-logs-by-trace","type":"search","attributes":{"title":"Community Observability: Logs By Trace","description":"Starter log view for filtering by trace.id or trace_id.","columns":["service.name","trace.id","trace_id","span_id","community.category","community.action","community.outcome","message"],"sort":[["@timestamp","desc"]],"grid":{},"hideChart":false,"isTextBasedQuery":false,"kibanaSavedObjectMeta":{"searchSourceJSON":"{\"query\":{\"language\":\"kuery\",\"query\":\"service.name : * and (trace.id : * or trace_id : *)\"},\"filter\":[],\"indexRefName\":\"kibanaSavedObjectMeta.searchSourceJSON.index\"}"},"version":1},"references":[{"id":"community-observability-logs","name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern"}],"typeMigrationVersion":"8.12.0","coreMigrationVersion":"8.12.0","managed":false,"namespaces":["default"]}
```

If the logs index pattern id in the file differs from `community-observability-logs`, use the exact existing logs index pattern id from the same file.

- [ ] **Step 6: Verify docs**

Run:

```bash
git diff --check -- docs/handbook/operations.md docs/handbook/local-development.md deploy/README.md deploy/observability/kibana/README.md deploy/observability/kibana/saved-objects.ndjson
if rg -n '需要应用 traces / metrics 时|traces-\* only becomes useful when `OTEL_ENABLED=true`' docs/handbook/operations.md deploy/observability/kibana/README.md; then
  echo "stale tracing wording remains" >&2
  exit 1
fi
```

Expected:

- `git diff --check` exits 0.
- The stale wording check emits nothing and exits 0.

- [ ] **Step 7: Commit**

```bash
git add docs/handbook/operations.md \
        docs/handbook/local-development.md \
        deploy/README.md \
        deploy/observability/kibana/README.md \
        deploy/observability/kibana/saved-objects.ndjson
git commit -m "docs: describe complete tracing workflow"
```

---

### Task 8: Final Verification And Acceptance Commands

**Files:**
- Verify only; no planned code changes.

- [ ] **Step 1: Run common module tests**

Run:

```bash
cd backend && mvn -q -pl community-common/common-core,community-common/common-kafka,community-common/common-spring,community-common/common-outbox test
```

Expected: PASS.

- [ ] **Step 2: Run app focused tests**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=JdbcOutboxEventStoreTest,OutboxWorkerRetryTest,OutboxWorkerSchedulerTest,AfterCommitExecutorTest,TraceJobRunnerTest,ImPolicyKafkaOutboxHandlerTest,PostOutboxEnqueuerTest,PostOutboxHandlerTest,SearchReindexHandlerTest,MarketOrderAutoConfirmHandlerTest,PendingRegistrationUserCleanupHandlerTest test
```

Expected: PASS.

- [ ] **Step 3: Run IM focused tests**

Run:

```bash
cd backend && mvn -q -pl community-im/im-core -Dtest=ImKafkaOutboxHandlerTest,CommandConsumersLoggingTest,ImCoreKafkaIntegrationTest,CommandConsumerIsolationIntegrationTest,PrivateMessageServiceTest,RoomMessageServiceTest test
```

Run:

```bash
cd backend && mvn -q -pl community-im/im-realtime -Dtest=CommandProducerTest,ImRealtimeWebSocketIntegrationTest test
```

Expected: PASS.

- [ ] **Step 4: Run deployment and docs checks**

Run:

```bash
bash -n deploy/deployment.sh deploy/tests/observability_otel_default.sh
./deploy/tests/observability_otel_default.sh
git diff --check -- docs/handbook deploy
```

Expected: PASS.

- [ ] **Step 5: Render compose configs**

Run:

```bash
./deploy/deployment.sh config --topology single --observability --env-file deploy/.env.single.example >/tmp/community-single-observability.yml
./deploy/deployment.sh config --topology cluster --observability --env-file deploy/.env.cluster.example >/tmp/community-cluster-observability.yml
rg -n 'OTEL_ENABLED[=: ]+true|OTEL_ENABLED=true' /tmp/community-single-observability.yml /tmp/community-cluster-observability.yml
```

Expected: PASS and the `rg` output shows all backend runtime services use `OTEL_ENABLED=true`.

- [ ] **Step 6: Optional local end-to-end acceptance**

Run when Docker resources are available:

```bash
./deploy/deployment.sh up --topology single --observability
./deploy/deployment.sh ps --topology single --observability
```

Expected:

- `community-app`, `community-gateway`, `im-core`, `im-realtime`, Elasticsearch, Kibana, and EDOT collector are healthy or running.
- `docker compose` rendered environment contains `OTEL_ENABLED=true` for backend services.

Then run one gateway REST request and inspect the returned trace headers:

```bash
curl -sS -D /tmp/community-trace-headers.txt http://localhost:12880/actuator/health >/tmp/community-health.json
rg -n 'X-Trace-Id|traceparent' /tmp/community-trace-headers.txt
```

Expected: Both `X-Trace-Id` and `traceparent` appear.

- [ ] **Step 7: Commit verification note only if new docs changed during verification**

If verification required doc corrections, commit them:

```bash
git add docs deploy
git commit -m "docs: refine tracing verification notes"
```

If no files changed, do not create an empty commit.
