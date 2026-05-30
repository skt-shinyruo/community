# Community Common JSON Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `community-common-json`, centralize project JSON serialization, remove Jackson infrastructure from `community-common-core`, and migrate production JSON call sites to the shared module without changing JSON contracts.

**Architecture:** `community-common-json` is a pure Java common module that owns Jackson mapper construction, a `JsonCodec` facade, codec exceptions, and event envelope JSON parsing. Spring modules expose `JsonCodec` beans backed by the application `ObjectMapper`; application and infrastructure code uses `JsonCodec` for explicit JSON work while DTOs keep Jackson annotations where they define contract shape.

**Tech Stack:** Java 17, Maven multi-module build, Spring Boot 3.2.6, Jackson databind, ArchUnit, JUnit 5, AssertJ.

---

## File Structure

Create:

- `backend/community-common/common-json/pom.xml`
- `backend/community-common/common-json/src/main/java/com/nowcoder/community/common/json/JsonCodec.java`
- `backend/community-common/common-json/src/main/java/com/nowcoder/community/common/json/JsonCodecException.java`
- `backend/community-common/common-json/src/main/java/com/nowcoder/community/common/json/JsonMappers.java`
- `backend/community-common/common-json/src/main/java/com/nowcoder/community/common/json/JacksonJsonCodec.java`
- `backend/community-common/common-json/src/main/java/com/nowcoder/community/common/json/EventEnvelopeJsonParser.java`
- `backend/community-common/common-json/src/test/java/com/nowcoder/community/common/json/JsonMappersTest.java`
- `backend/community-common/common-json/src/test/java/com/nowcoder/community/common/json/JacksonJsonCodecTest.java`
- `backend/community-common/common-json/src/test/java/com/nowcoder/community/common/json/EventEnvelopeJsonParserTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/JsonBoundaryArchTest.java`

Modify:

- `backend/community-common/pom.xml`
- `backend/community-common/common-core/pom.xml`
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventEnvelope.java`
- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventEnvelopeParser.java`
- `backend/community-common/common-web/pom.xml`
- `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/CommonJacksonConfig.java`
- `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/SecurityExceptionHandler.java`
- `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/autoconfig/ServletWebInfraAutoConfiguration.java`
- `backend/community-common/common-web/src/test/java/com/nowcoder/community/common/web/SecurityExceptionHandlerTest.java`
- `backend/community-common/common-webflux/pom.xml`
- `backend/community-common/common-webflux/src/main/java/com/nowcoder/community/common/webflux/SecurityExceptionHandler.java`
- `backend/community-common/common-webflux/src/main/java/com/nowcoder/community/common/webflux/autoconfig/WebFluxInfraAutoConfiguration.java`
- `backend/community-common/common-webflux/src/test/java/com/nowcoder/community/common/webflux/SecurityExceptionHandlerTest.java`
- `backend/community-common/common-idempotency/pom.xml`
- `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyGuard.java`
- `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/autoconfig/IdempotencyGuardAutoConfiguration.java`
- `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/IdempotencyGuardFingerprintTest.java`
- `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/IdempotencyGuardStoreFailureTest.java`
- `backend/community-app/pom.xml`
- `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/CommentTaskProgressOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/CommentTaskProgressOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyChangePublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyKafkaOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/web/AuthOriginGuardFilter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRefreshTokenRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationDraftRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentBlockRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/model/NoticeProjection.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
- Tests named in Task 5 Step 2 under `backend/community-app/src/test/java`
- `backend/community-im/im-core/pom.xml`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/outbox/ImMessageOutboxEnqueuer.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/outbox/ImKafkaOutboxHandler.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/outbox/ImOutboxConfiguration.java`
- Tests named in Task 6 Step 2 under `backend/community-im/im-core/src/test/java`
- `backend/community-im/im-realtime/pom.xml`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImFrameCodec.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/push/RoomUpdateCoalescer.java`
- Tests named in Task 6 Step 2 under `backend/community-im/im-realtime/src/test/java`
- `backend/community-im-gateway/pom.xml`
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ImGatewayFrameCodec.java`
- `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ConnectTicketRouter.java`
- `backend/community-oss-client/pom.xml`
- `backend/community-oss-client/src/main/java/com/nowcoder/community/oss/client/HttpCommunityOssClient.java`
- `backend/community-oss-client/src/test/java/com/nowcoder/community/oss/client/HttpCommunityOssClientTest.java`

Delete:

- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventEnvelopeParser.java`

## Task 1: Create `community-common-json`

**Files:**
- Create: `backend/community-common/common-json/pom.xml`
- Create: `backend/community-common/common-json/src/test/java/com/nowcoder/community/common/json/JsonMappersTest.java`
- Create: `backend/community-common/common-json/src/test/java/com/nowcoder/community/common/json/JacksonJsonCodecTest.java`
- Create: `backend/community-common/common-json/src/test/java/com/nowcoder/community/common/json/EventEnvelopeJsonParserTest.java`
- Create: `backend/community-common/common-json/src/main/java/com/nowcoder/community/common/json/JsonCodec.java`
- Create: `backend/community-common/common-json/src/main/java/com/nowcoder/community/common/json/JsonCodecException.java`
- Create: `backend/community-common/common-json/src/main/java/com/nowcoder/community/common/json/JsonMappers.java`
- Create: `backend/community-common/common-json/src/main/java/com/nowcoder/community/common/json/JacksonJsonCodec.java`
- Create: `backend/community-common/common-json/src/main/java/com/nowcoder/community/common/json/EventEnvelopeJsonParser.java`
- Modify: `backend/community-common/pom.xml`

- [ ] **Step 1: Register the module skeleton and write failing tests**

Add `<module>common-json</module>` to `backend/community-common/pom.xml` after `common-core`.

Create `backend/community-common/common-json/pom.xml`:

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

    <artifactId>community-common-json</artifactId>
    <name>community-common-json</name>
    <description>Business-agnostic JSON codec and Jackson configuration</description>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>community-common-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
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
</project>
```

Create `JsonMappersTest.java`:

```java
package com.nowcoder.community.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonMappersTest {

    @Test
    void standardMapperShouldSerializeJavaTimeAsIsoText() throws Exception {
        ObjectMapper mapper = JsonMappers.standard();

        String json = mapper.writeValueAsString(new TimePayload(Instant.parse("2026-05-30T00:00:00Z")));

        assertThat(json).contains("\"at\":\"2026-05-30T00:00:00Z\"");
    }

    @Test
    void standardMapperShouldIgnoreUnknownProperties() throws Exception {
        ObjectMapper mapper = JsonMappers.standard();

        KnownField value = mapper.readValue("{\"name\":\"json\",\"extra\":1}", KnownField.class);

        assertThat(value.name()).isEqualTo("json");
    }

    @Test
    void standardMapperShouldOmitNullFieldsForEventEnvelopeOnly() throws Exception {
        ObjectMapper mapper = JsonMappers.standard();
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.of("demo.type", 1, null, Map.of("k", "v"), "trace-1");

        JsonNode node = mapper.readTree(mapper.writeValueAsString(envelope));

        assertThat(node.has("producer")).isFalse();
        assertThat(node.path("payload").path("k").asText()).isEqualTo("v");
    }

    record TimePayload(Instant at) {
    }

    record KnownField(String name) {
    }
}
```

Create `JacksonJsonCodecTest.java`:

```java
package com.nowcoder.community.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonJsonCodecTest {

    private final JsonCodec codec = new JacksonJsonCodec(JsonMappers.standard());

    @Test
    void shouldSerializeAndDeserializeTypedValues() {
        String json = codec.toJson(new DemoPayload("a"));

        DemoPayload copy = codec.fromJson(json, DemoPayload.class);

        assertThat(copy.value()).isEqualTo("a");
    }

    @Test
    void shouldExposeTreeOperations() {
        JsonNode node = codec.readTree("{\"value\":\"a\"}");

        DemoPayload copy = codec.treeToValue(node, DemoPayload.class);
        JsonNode tree = codec.valueToTree(copy);

        assertThat(copy.value()).isEqualTo("a");
        assertThat(tree.path("value").asText()).isEqualTo("a");
    }

    @Test
    void shouldWrapSerializationFailures() {
        Map<String, Object> self = new HashMap<>();
        self.put("self", self);

        assertThatThrownBy(() -> codec.toJson(self))
                .isInstanceOf(JsonCodecException.class)
                .hasMessageContaining("serialize");
    }

    @Test
    void shouldWrapDeserializationFailures() {
        assertThatThrownBy(() -> codec.fromJson("{", DemoPayload.class))
                .isInstanceOf(JsonCodecException.class)
                .hasMessageContaining("deserialize");
    }

    record DemoPayload(String value) {
    }
}
```

Create `EventEnvelopeJsonParserTest.java`:

```java
package com.nowcoder.community.common.json;

import com.nowcoder.community.common.event.EventEnvelope;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeJsonParserTest {

    private final JsonCodec codec = new JacksonJsonCodec(JsonMappers.standard());

    @Test
    void shouldParseEnvelopeAndExposePayloadTree() {
        EventEnvelope<Object> env = EventEnvelope.of("demo.type", 1, "demo-producer", Map.of("k", "v"), "trace-1");
        String json = codec.toJson(env);

        EventEnvelopeJsonParser.ParsedEnvelope parsed = EventEnvelopeJsonParser.parse(codec, json);

        assertThat(parsed.getEventId()).isNotBlank();
        assertThat(parsed.getType()).isEqualTo("demo.type");
        assertThat(parsed.getVersion()).isEqualTo(1);
        assertThat(parsed.getTraceId()).isEqualTo("trace-1");
        assertThat(parsed.getProducer()).isEqualTo("demo-producer");
        assertThat(parsed.getOccurredAt()).isNotNull();
        assertThat(parsed.getPayload().path("k").asText()).isEqualTo("v");
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        assertThatThrownBy(() -> EventEnvelopeJsonParser.parse(codec, "{\"type\":\"demo.type\",\"version\":1,\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void shouldRejectInvalidJson() {
        assertThatThrownBy(() -> EventEnvelopeJsonParser.parse(codec, "{"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法 JSON");
    }
}
```

- [ ] **Step 2: Run tests to verify the new API is missing**

Run:

```bash
cd backend
mvn test -pl :community-common-json -am
```

Expected: compilation fails because `JsonMappers`, `JsonCodec`, `JacksonJsonCodec`, `JsonCodecException`, and `EventEnvelopeJsonParser` do not exist.

- [ ] **Step 3: Implement `JsonCodec` and `JsonCodecException`**

Create `JsonCodec.java`:

```java
package com.nowcoder.community.common.json;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonCodec {

    String toJson(Object value);

    <T> T fromJson(String json, Class<T> type);

    JsonNode readTree(String json);

    <T> T treeToValue(JsonNode node, Class<T> type);

    JsonNode valueToTree(Object value);
}
```

Create `JsonCodecException.java`:

```java
package com.nowcoder.community.common.json;

public class JsonCodecException extends RuntimeException {

    public JsonCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Implement mapper factory and codec**

Create `JsonMappers.java`:

```java
package com.nowcoder.community.common.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nowcoder.community.common.event.EventEnvelope;

public final class JsonMappers {

    private JsonMappers() {
    }

    public static ObjectMapper standard() {
        return standardBuilder().build();
    }

    public static JsonMapper.Builder standardBuilder() {
        return JsonMapper.builder()
                .findAndAddModules()
                .addMixIn(EventEnvelope.class, EventEnvelopeMixin.class)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class EventEnvelopeMixin {
    }
}
```

Create `JacksonJsonCodec.java`:

```java
package com.nowcoder.community.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public class JacksonJsonCodec implements JsonCodec {

    private final ObjectMapper objectMapper;

    public JacksonJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("serialize json failed", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("deserialize json failed", e);
        }
    }

    @Override
    public JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("deserialize json tree failed", e);
        }
    }

    @Override
    public <T> T treeToValue(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("deserialize json tree failed", e);
        }
    }

    @Override
    public JsonNode valueToTree(Object value) {
        try {
            return objectMapper.valueToTree(value);
        } catch (IllegalArgumentException e) {
            throw new JsonCodecException("serialize json tree failed", e);
        }
    }
}
```

- [ ] **Step 5: Implement event envelope JSON parser**

Create `EventEnvelopeJsonParser.java`:

```java
package com.nowcoder.community.common.json;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;

public final class EventEnvelopeJsonParser {

    private EventEnvelopeJsonParser() {
    }

    public static ParsedEnvelope parse(JsonCodec jsonCodec, String json) {
        if (jsonCodec == null) {
            throw new IllegalArgumentException("jsonCodec 缺失");
        }
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("record value 为空");
        }
        JsonNode root;
        try {
            root = jsonCodec.readTree(json);
        } catch (JsonCodecException e) {
            throw new IllegalArgumentException("record value 非法 JSON", e);
        }
        String eventId = text(root, "eventId");
        String type = text(root, "type");
        int version = root.path("version").asInt(0);
        String traceId = text(root, "traceId");
        String producer = text(root, "producer");
        Instant occurredAt = parseInstant(text(root, "occurredAt"));
        JsonNode payload = root.get("payload");

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 缺失");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type 缺失");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version 缺失");
        }
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("payload 缺失");
        }

        ParsedEnvelope e = new ParsedEnvelope();
        e.eventId = eventId;
        e.type = type;
        e.version = version;
        e.traceId = traceId;
        e.producer = producer;
        e.occurredAt = occurredAt;
        e.payload = payload;
        return e;
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(s.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String text(JsonNode root, String field) {
        if (root == null || field == null || field.isBlank()) {
            return null;
        }
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String s = node.asText();
        return s == null || s.isBlank() ? null : s;
    }

    public static class ParsedEnvelope {
        private String eventId;
        private String type;
        private int version;
        private String traceId;
        private Instant occurredAt;
        private String producer;
        private JsonNode payload;

        public String getEventId() {
            return eventId;
        }

        public String getType() {
            return type;
        }

        public int getVersion() {
            return version;
        }

        public String getTraceId() {
            return traceId;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }

        public String getProducer() {
            return producer;
        }

        public JsonNode getPayload() {
            return payload;
        }
    }
}
```

- [ ] **Step 6: Run tests to verify the module passes**

Run:

```bash
cd backend
mvn test -pl :community-common-json -am
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add backend/community-common/pom.xml backend/community-common/common-json
git commit -m "feat: add common json module"
```

## Task 2: Remove Jackson From `community-common-core`

**Files:**
- Modify: `backend/community-common/common-core/pom.xml`
- Modify: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventEnvelope.java`
- Delete: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventEnvelopeParser.java`

- [ ] **Step 1: Remove the Jackson dependency from `common-core`**

Delete this dependency block from `backend/community-common/common-core/pom.xml`:

```xml
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
```

- [ ] **Step 2: Run tests to verify `common-core` still references Jackson**

Run:

```bash
cd backend
mvn test -pl :community-common-core -am
```

Expected: compilation fails because `EventEnvelope` imports `JsonInclude` and the legacy parser imports Jackson classes.

- [ ] **Step 3: Make `EventEnvelope` Jackson-free**

In `EventEnvelope.java`, remove:

```java
import com.fasterxml.jackson.annotation.JsonInclude;
```

and remove:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
```

Keep the rest of the class unchanged.

- [ ] **Step 4: Delete the old parser**

Delete:

```text
backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventEnvelopeParser.java
```

The replacement is `com.nowcoder.community.common.json.EventEnvelopeJsonParser`.

- [ ] **Step 5: Run core and JSON module tests**

Run:

```bash
cd backend
mvn test -pl :community-common-core,:community-common-json -am
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add backend/community-common/common-core/pom.xml backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventEnvelope.java backend/community-common/common-core/src/main/java/com/nowcoder/community/common/event/EventEnvelopeParser.java
git commit -m "refactor: remove jackson from common core"
```

## Task 3: Wire JSON Codec Into Shared Spring Modules

**Files:**
- Modify: `backend/community-common/common-web/pom.xml`
- Modify: `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/CommonJacksonConfig.java`
- Modify: `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/SecurityExceptionHandler.java`
- Modify: `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/autoconfig/ServletWebInfraAutoConfiguration.java`
- Modify: `backend/community-common/common-web/src/test/java/com/nowcoder/community/common/web/SecurityExceptionHandlerTest.java`
- Modify: `backend/community-common/common-webflux/pom.xml`
- Modify: `backend/community-common/common-webflux/src/main/java/com/nowcoder/community/common/webflux/SecurityExceptionHandler.java`
- Modify: `backend/community-common/common-webflux/src/main/java/com/nowcoder/community/common/webflux/autoconfig/WebFluxInfraAutoConfiguration.java`
- Modify: `backend/community-common/common-webflux/src/test/java/com/nowcoder/community/common/webflux/SecurityExceptionHandlerTest.java`

- [ ] **Step 1: Write failing security handler tests against `JsonCodec`**

In both security handler tests, add helpers:

```java
private static JsonCodec jsonCodec() {
    return new JacksonJsonCodec(JsonMappers.standard());
}
```

Change handler construction from:

```java
new SecurityExceptionHandler(objectMapper)
```

to:

```java
new SecurityExceptionHandler(jsonCodec())
```

Add imports:

```java
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
```

Run:

```bash
cd backend
mvn test -pl :community-common-web,:community-common-webflux -am -Dtest='SecurityExceptionHandlerTest'
```

Expected: compilation fails because the handlers still require `ObjectMapper`.

- [ ] **Step 2: Add `community-common-json` dependencies**

Add this dependency to `common-web/pom.xml` and `common-webflux/pom.xml` after `community-common-core`:

```xml
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>community-common-json</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 3: Update servlet security handler to use `JsonCodec`**

In `common-web/SecurityExceptionHandler.java`, replace the `ObjectMapper` field and constructor with:

```java
private final JsonCodec jsonCodec;

public SecurityExceptionHandler(JsonCodec jsonCodec) {
    this.jsonCodec = jsonCodec;
}
```

Replace:

```java
response.getWriter().write(objectMapper.writeValueAsString(body));
```

with:

```java
response.getWriter().write(jsonCodec.toJson(body));
```

Add:

```java
import com.nowcoder.community.common.json.JsonCodec;
```

Remove:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
```

- [ ] **Step 4: Update WebFlux security handler to use `JsonCodec`**

In `common-webflux/SecurityExceptionHandler.java`, replace the field and constructor with:

```java
private final JsonCodec jsonCodec;

public SecurityExceptionHandler(JsonCodec jsonCodec) {
    this.jsonCodec = jsonCodec;
}
```

Replace:

```java
bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
```

with:

```java
bytes = jsonCodec.toJson(body).getBytes(StandardCharsets.UTF_8);
```

Add:

```java
import com.nowcoder.community.common.json.JsonCodec;
```

Remove:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
```

- [ ] **Step 5: Expose `JsonCodec` beans from Spring bridge modules**

In `ServletWebInfraAutoConfiguration.java`, add imports:

```java
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
```

Add this bean before `securityExceptionHandler`:

```java
@Bean
@ConditionalOnMissingBean
public JsonCodec jsonCodec(ObjectMapper objectMapper) {
    return new JacksonJsonCodec(objectMapper);
}
```

Change:

```java
public SecurityExceptionHandler securityExceptionHandler(ObjectMapper objectMapper) {
    return new SecurityExceptionHandler(objectMapper);
}
```

to:

```java
public SecurityExceptionHandler securityExceptionHandler(JsonCodec jsonCodec) {
    return new SecurityExceptionHandler(jsonCodec);
}
```

In `WebFluxInfraAutoConfiguration.java`, apply the same `jsonCodec(ObjectMapper objectMapper)` bean and change `securityExceptionHandler` to accept `JsonCodec`.

- [ ] **Step 6: Mirror shared mapper behavior in servlet Jackson config**

In `CommonJacksonConfig.java`, add:

```java
import com.fasterxml.jackson.databind.DeserializationFeature;
```

Change the customizer to:

```java
return builder -> builder.featuresToDisable(
        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
);
```

- [ ] **Step 7: Run shared web tests**

Run:

```bash
cd backend
mvn test -pl :community-common-web,:community-common-webflux -am -Dtest='SecurityExceptionHandlerTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add backend/community-common/common-web backend/community-common/common-webflux
git commit -m "refactor: wire common json codec into web modules"
```

## Task 4: Migrate Common Idempotency To `JsonCodec`

**Files:**
- Modify: `backend/community-common/common-idempotency/pom.xml`
- Modify: `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyGuard.java`
- Modify: `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/autoconfig/IdempotencyGuardAutoConfiguration.java`
- Modify: `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/IdempotencyGuardFingerprintTest.java`
- Modify: `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/IdempotencyGuardStoreFailureTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/idempotency/IdempotencyGuardSerializationFailureTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/idempotency/IdempotencyGuardTtlTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`

- [ ] **Step 1: Write failing constructor updates in idempotency tests**

Add this helper to idempotency tests that instantiate `IdempotencyGuard` directly:

```java
private static JsonCodec jsonCodec() {
    return new JacksonJsonCodec(JsonMappers.standard());
}
```

Change direct constructor calls from:

```java
new IdempotencyGuard(new ObjectMapper(), store, null, properties)
```

to:

```java
new IdempotencyGuard(jsonCodec(), store, null, properties)
```

Add imports:

```java
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
```

For `IdempotencyGuardSerializationFailureTest`, mock `JsonCodec` instead of `ObjectMapper`:

```java
JsonCodec jsonCodec = mock(JsonCodec.class);
when(jsonCodec.toJson(any())).thenThrow(new JsonCodecException("boom", new RuntimeException("boom")));
```

Run:

```bash
cd backend
mvn test -pl :community-common-idempotency,:community-app -am -Dtest='IdempotencyGuard*Test,CommentApplicationServiceTest'
```

Expected: compilation fails because `IdempotencyGuard` still accepts `ObjectMapper`.

- [ ] **Step 2: Add `community-common-json` dependency**

Add this dependency to `common-idempotency/pom.xml` after `community-common-core`:

```xml
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>community-common-json</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 3: Change `IdempotencyGuard` to use `JsonCodec`**

In `IdempotencyGuard.java`, replace the field and constructor parameter:

```java
private final JsonCodec jsonCodec;

public IdempotencyGuard(
        JsonCodec jsonCodec,
        IdempotencyStore store,
        ObjectProvider<MeterRegistry> meterRegistryProvider,
        IdempotencyProperties properties
) {
    this.jsonCodec = jsonCodec;
    if (store == null) {
        throw new IllegalStateException("idempotency store is null");
    }
    this.store = store;
    this.meterRegistryProvider = meterRegistryProvider;
    this.properties = properties == null ? new IdempotencyProperties() : properties;
}
```

Replace `toJson` and `fromJson` with:

```java
private String toJson(Object value) {
    if (value == null) {
        return "null";
    }
    try {
        return jsonCodec == null ? String.valueOf(value) : jsonCodec.toJson(value);
    } catch (JsonCodecException e) {
        throw new IllegalStateException("serialize idempotency response failed", e);
    }
}

private <T> T fromJson(String json, Class<T> type) {
    if (type == null || type == Void.class) {
        return null;
    }
    if (!StringUtils.hasText(json) || "null".equals(json)) {
        return null;
    }
    try {
        if (jsonCodec == null) {
            throw new IllegalStateException("jsonCodec is null");
        }
        return jsonCodec.fromJson(json, type);
    } catch (JsonCodecException e) {
        throw new IllegalStateException("deserialize idempotency response failed", e);
    }
}
```

Add imports:

```java
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
```

Remove Jackson imports from the class.

- [ ] **Step 4: Update idempotency auto-configuration**

In `IdempotencyGuardAutoConfiguration.java`, replace `ObjectMapper` with `JsonCodec`:

```java
import com.nowcoder.community.common.json.JsonCodec;
```

Change:

```java
@ConditionalOnClass(ObjectMapper.class)
```

to:

```java
@ConditionalOnClass(JsonCodec.class)
```

Change the bean parameter:

```java
JsonCodec jsonCodec,
```

and construct:

```java
return new IdempotencyGuard(jsonCodec, store, meterRegistryProvider, properties);
```

- [ ] **Step 5: Run idempotency tests**

Run:

```bash
cd backend
mvn test -pl :community-common-idempotency,:community-app -am -Dtest='IdempotencyGuard*Test,CommentApplicationServiceTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add backend/community-common/common-idempotency backend/community-app/src/test/java/com/nowcoder/community/infra/idempotency backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java
git commit -m "refactor: use json codec for idempotency"
```

## Task 5: Migrate `community-app` JSON Consumers

**Files:**
- Modify: `backend/community-app/pom.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxEnqueuer.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/CommentTaskProgressOutboxEnqueuer.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/CommentTaskProgressOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxEnqueuer.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyChangePublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyKafkaOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/web/AuthOriginGuardFilter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRefreshTokenRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationDraftRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentBlockRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/model/NoticeProjection.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
- Modify: tests named in Step 2 under `backend/community-app/src/test/java`

- [ ] **Step 1: Add direct `community-common-json` dependency to `community-app`**

Add after `community-common-core` in `backend/community-app/pom.xml`:

```xml
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>community-common-json</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 2: Update tests to inject `JsonCodec` in app JSON consumers**

In tests that instantiate app JSON consumers directly, add this helper:

```java
private static JsonCodec jsonCodec() {
    return new JacksonJsonCodec(JsonMappers.standard());
}
```

Replace constructor arguments of type `ObjectMapper` with `jsonCodec()` for:

- `PostOutboxEnqueuer`
- `PostOutboxHandler`
- `CommentTaskProgressOutboxEnqueuer`
- `CommentTaskProgressOutboxHandler`
- `CommentRewardOutboxEnqueuer`
- `CommentRewardOutboxHandler`
- `ImPolicyKafkaOutboxHandler`
- `AuthOriginGuardFilter`
- `RedisRefreshTokenRepository`
- `RedisRegistrationDraftRepository`
- `NoticeProjectionApplicationService`
- `MyBatisPostContentBlockRepository`

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='PostOutbox*Test,CommentTaskProgress*Test,CommentReward*Test,ImPolicyKafkaOutboxHandlerTest,AuthOriginGuardFilterTest,RedisRefreshTokenRepositoryTest,RedisRegistrationDraftRepositoryTest,NoticeProjectionListenerTest,NoticeProjectionDomainServiceTest'
```

Expected: compilation fails because production constructors still require `ObjectMapper`.

- [ ] **Step 3: Apply the standard constructor replacement in production classes**

For every production class listed in this task that has:

```java
private final ObjectMapper objectMapper;
```

replace it with:

```java
private final JsonCodec jsonCodec;
```

For constructors, replace:

```java
ObjectMapper objectMapper
```

with:

```java
JsonCodec jsonCodec
```

and replace:

```java
this.objectMapper = objectMapper;
```

with:

```java
this.jsonCodec = jsonCodec;
```

Add:

```java
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
```

Remove:

```java
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
```

Keep `JsonNode` imports only in classes that still inspect tree payloads, such as `ImPolicyKafkaOutboxHandler`.

- [ ] **Step 4: Replace serialization and deserialization calls**

Use these exact substitutions:

```java
objectMapper.writeValueAsString(value)
```

becomes:

```java
jsonCodec.toJson(value)
```

```java
objectMapper.readValue(json, SomeType.class)
```

becomes:

```java
jsonCodec.fromJson(json, SomeType.class)
```

```java
objectMapper.readTree(json)
```

becomes:

```java
jsonCodec.readTree(json)
```

```java
objectMapper.valueToTree(value)
```

becomes:

```java
jsonCodec.valueToTree(value)
```

Change catch blocks from:

```java
} catch (JsonProcessingException e) {
```

to:

```java
} catch (JsonCodecException e) {
```

Preserve existing exception messages.

- [ ] **Step 5: Remove `JsonNode` from the notice domain model**

Change `NoticeProjection.java` to:

```java
package com.nowcoder.community.notice.domain.model;

import java.util.UUID;

public record NoticeProjection(
        UUID toUserId,
        String topic,
        String sourceEventId,
        String sourceEventType,
        Object payload
) {
}
```

In `NoticeProjectionApplicationService`, remove `objectMapper.valueToTree(payload)` and construct:

```java
return new NoticeProjection(toUserId, topic, eventId, eventType, payload);
```

Serialize notice content with:

```java
String contentJson = jsonCodec.toJson(Map.of(
        "eventId", projection.sourceEventId(),
        "type", projection.sourceEventType(),
        "payload", projection.payload()
));
```

- [ ] **Step 6: Update content block metadata parsing without `TypeReference`**

In `MyBatisPostContentBlockRepository`, remove:

```java
import com.fasterxml.jackson.core.type.TypeReference;
```

Remove:

```java
private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
};
```

Replace `readMetadata` with:

```java
private Map<String, Object> readMetadata(String metadataJson) {
    if (metadataJson == null || metadataJson.isBlank()) {
        return Map.of();
    }
    try {
        Map<?, ?> raw = jsonCodec.fromJson(metadataJson, Map.class);
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key instanceof String s) {
                metadata.put(s, value);
            }
        });
        return Collections.unmodifiableMap(metadata);
    } catch (JsonCodecException e) {
        throw new BusinessException(INTERNAL_ERROR, "内容块元数据反序列化失败", e);
    }
}
```

Keep `writeMetadata` behavior by replacing the mapper call with:

```java
return jsonCodec.toJson(safeMetadata);
```

- [ ] **Step 7: Preserve Redis repository failure semantics**

In `RedisRefreshTokenRepository`, keep these semantics:

```java
} catch (JsonCodecException e) {
    throw new IllegalStateException("refresh token 序列化失败", e);
}
```

For tombstone serialization failure, keep best-effort behavior:

```java
} catch (JsonCodecException ignored) {
}
```

For record and tombstone reads, keep corrupt JSON as a miss:

```java
} catch (JsonCodecException e) {
    return null;
}
```

In `RedisRegistrationDraftRepository`, keep corrupt JSON cleanup:

```java
} catch (JsonCodecException ex) {
    delete(token);
    return Optional.empty();
}
```

- [ ] **Step 8: Run app focused tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='PostOutbox*Test,CommentTaskProgress*Test,CommentReward*Test,ImPolicyKafkaOutboxHandlerTest,AuthOriginGuardFilterTest,RedisRefreshTokenRepositoryTest,RedisRegistrationDraftRepositoryTest,NoticeProjectionListenerTest,NoticeProjectionDomainServiceTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```bash
git add backend/community-app
git commit -m "refactor: use common json codec in app"
```

## Task 6: Migrate IM And OSS JSON Consumers

**Files:**
- Modify: `backend/community-im/im-core/pom.xml`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/outbox/ImMessageOutboxEnqueuer.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/outbox/ImKafkaOutboxHandler.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/outbox/ImOutboxConfiguration.java`
- Modify: `backend/community-im/im-realtime/pom.xml`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImFrameCodec.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/push/RoomUpdateCoalescer.java`
- Modify: `backend/community-im-gateway/pom.xml`
- Modify: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ImGatewayFrameCodec.java`
- Modify: `backend/community-im-gateway/src/main/java/com/nowcoder/community/im/gateway/ws/ConnectTicketRouter.java`
- Modify: `backend/community-oss-client/pom.xml`
- Modify: `backend/community-oss-client/src/main/java/com/nowcoder/community/oss/client/HttpCommunityOssClient.java`
- Modify: tests named in Step 2 in these modules

- [ ] **Step 1: Add direct `community-common-json` dependencies**

Add this dependency to `im-core`, `im-realtime`, `community-im-gateway`, and `community-oss-client` POM files:

```xml
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>community-common-json</artifactId>
            <version>${project.version}</version>
        </dependency>
```

Place it near the other `com.nowcoder.community` dependencies.

- [ ] **Step 2: Update tests to expect `JsonCodec` constructor arguments**

Use the same helper in affected tests:

```java
private static JsonCodec jsonCodec() {
    return new JacksonJsonCodec(JsonMappers.standard());
}
```

Replace direct `new ObjectMapper()` constructor arguments for:

- `ImMessageOutboxEnqueuer`
- `ImKafkaOutboxHandler`
- `ImFrameCodec`
- `RoomUpdateCoalescer`
- `ImGatewayFrameCodec`

Run:

```bash
cd backend
mvn test -pl :im-core,:im-realtime,:community-im-gateway,:community-oss-client -am -Dtest='ImMessageOutboxEnqueuerTest,ImKafkaOutboxHandlerTest,RoomUpdateCoalescerTest,ImWebSocketHandlerContractVersionTest,ImRealtimeWebSocketIntegrationTest,HttpCommunityOssClientTest'
```

Expected: compilation fails because production constructors still require `ObjectMapper`.

- [ ] **Step 3: Migrate IM core outbox classes**

In `ImMessageOutboxEnqueuer`, `ImKafkaOutboxHandler`, and `ImOutboxConfiguration`, replace `ObjectMapper` fields, constructor parameters, and bean method parameters with `JsonCodec`.

Use:

```java
jsonCodec.toJson(payload)
```

for enqueue payload serialization.

Use:

```java
payload = jsonCodec.fromJson(event.payload(), payloadType);
```

for handler payload deserialization.

Catch `JsonCodecException` where the current code catches `JsonProcessingException`, preserving existing log messages and exception types.

- [ ] **Step 4: Migrate realtime and gateway frame codecs**

In `ImFrameCodec`, replace the field and constructor:

```java
private final JsonCodec jsonCodec;

public ImFrameCodec(JsonCodec jsonCodec) {
    this.jsonCodec = jsonCodec;
}
```

Use:

```java
return jsonCodec.readTree(text);
return jsonCodec.treeToValue(node, type);
return jsonCodec.toJson(value);
```

Catch `JsonCodecException`.

For schema version handling, keep the existing cause-chain behavior by changing `hasUnsupportedSchemaVersion(e)` to inspect the `JsonCodecException` cause chain:

```java
} catch (JsonCodecException e) {
    if (hasUnsupportedSchemaVersion(e)) {
        throw new ImUnsupportedSchemaVersionException(unsupportedSchemaVersion(e), supportedSchemaVersion(e));
    }
    throw new IllegalArgumentException("invalid websocket frame payload", e);
}
```

In `ImGatewayFrameCodec`, apply the same constructor and method replacements, without the IM schema-version branch.

`ConnectTicketRouter` can keep using `JsonNode`; it should continue to call `frameCodec.readTree` and `frameCodec.read`.

- [ ] **Step 5: Migrate `RoomUpdateCoalescer`**

Replace its mapper field and constructor with `JsonCodec`.

Replace:

```java
json = objectMapper.writeValueAsString(new RoomUpdatedBatch(items));
```

with:

```java
json = jsonCodec.toJson(new RoomUpdatedBatch(items));
```

Catch `JsonCodecException` and preserve the current failure behavior.

- [ ] **Step 6: Migrate OSS client envelope parsing**

In `HttpCommunityOssClient`, replace:

```java
private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .findAndAddModules()
        .build();
```

with:

```java
private static final JsonCodec JSON = new JacksonJsonCodec(JsonMappers.standard());
```

Replace:

```java
JsonNode root = OBJECT_MAPPER.readTree(responseBody);
...
return OBJECT_MAPPER.treeToValue(payload, responseType);
```

with:

```java
JsonNode root = JSON.readTree(responseBody);
...
return JSON.treeToValue(payload, responseType);
```

Catch:

```java
} catch (JsonCodecException e) {
    throw new IllegalStateException("failed to parse OSS response", e);
}
```

Remove imports for `ObjectMapper`, `JsonMapper`, and `JsonProcessingException`.

- [ ] **Step 7: Run IM and OSS focused tests**

Run:

```bash
cd backend
mvn test -pl :im-core,:im-realtime,:community-im-gateway,:community-oss-client -am -Dtest='ImMessageOutboxEnqueuerTest,ImKafkaOutboxHandlerTest,RoomUpdateCoalescerTest,ImWebSocketHandlerContractVersionTest,ImRealtimeWebSocketIntegrationTest,HttpCommunityOssClientTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add backend/community-im backend/community-im-gateway backend/community-oss-client
git commit -m "refactor: use common json codec in im and oss clients"
```

## Task 7: Add JSON Boundary Guardrails

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/JsonBoundaryArchTest.java`

- [ ] **Step 1: Write the guardrail test**

Create `JsonBoundaryArchTest.java`:

```java
package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class JsonBoundaryArchTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_jackson_databind =
            noClasses()
                    .that().resideInAnyPackage(
                            "..domain.model..",
                            "..domain.service..",
                            "..domain.repository..",
                            "..domain.event.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson.databind..")
                    .because("domain code must not depend on JSON tree or mapper infrastructure");

    @ArchTest
    static final ArchRule production_code_must_not_depend_on_object_mapper_outside_json_infrastructure =
            noClasses()
                    .that().resideOutsideOfPackages(
                            "com.nowcoder.community.common.json..",
                            "com.nowcoder.community.common.web..",
                            "com.nowcoder.community.common.webflux.."
                    )
                    .should().dependOnClassesThat().haveFullyQualifiedName("com.fasterxml.jackson.databind.ObjectMapper")
                    .because("explicit JSON serialization should use community-common-json");

    @ArchTest
    static final ArchRule production_code_must_not_depend_on_json_mapper_outside_json_infrastructure =
            noClasses()
                    .that().resideOutsideOfPackages(
                            "com.nowcoder.community.common.json..",
                            "com.nowcoder.community.common.web..",
                            "com.nowcoder.community.common.webflux.."
                    )
                    .should().dependOnClassesThat().haveFullyQualifiedName("com.fasterxml.jackson.databind.json.JsonMapper")
                    .because("explicit JSON serialization should use community-common-json");

    @ArchTest
    static final ArchRule production_code_must_not_depend_on_serialization_feature_outside_json_infrastructure =
            noClasses()
                    .that().resideOutsideOfPackages(
                            "com.nowcoder.community.common.json..",
                            "com.nowcoder.community.common.web..",
                            "com.nowcoder.community.common.webflux.."
                    )
                    .should().dependOnClassesThat().haveFullyQualifiedName("com.fasterxml.jackson.databind.SerializationFeature")
                    .because("explicit JSON serialization should use community-common-json");

    @ArchTest
    static final ArchRule production_code_must_not_depend_on_deserialization_feature_outside_json_infrastructure =
            noClasses()
                    .that().resideOutsideOfPackages(
                            "com.nowcoder.community.common.json..",
                            "com.nowcoder.community.common.web..",
                            "com.nowcoder.community.common.webflux.."
                    )
                    .should().dependOnClassesThat().haveFullyQualifiedName("com.fasterxml.jackson.databind.DeserializationFeature")
                    .because("explicit JSON serialization should use community-common-json");

    @ArchTest
    static final ArchRule common_core_must_not_depend_on_jackson_databind =
            noClasses()
                    .that().resideInAnyPackage("com.nowcoder.community.common.event..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson.databind..")
                    .because("common-core event models must stay JSON-library neutral");
}
```

- [ ] **Step 2: Run the new guardrail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='JsonBoundaryArchTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run all app architecture tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/app/arch/JsonBoundaryArchTest.java
git commit -m "test: add json boundary guardrails"
```

## Task 8: Sweep Remaining Production JSON Construction And Run Verification

**Files:**
- Modify only files reported by the searches in this task.

- [ ] **Step 1: Search for forbidden production mapper construction**

Run:

```bash
cd /home/feng/code/project/community
rg -n --glob '!**/src/test/**' 'new ObjectMapper|JsonMapper\\.builder|SerializationFeature|DeserializationFeature|JsonProcessingException|private final ObjectMapper|ObjectMapper objectMapper' backend
```

Expected after previous tasks: matches are limited to approved Spring bridge code, Jackson annotations, generated `target` output, or no production matches. If `target` output appears, remove it from the working tree with `mvn clean` rather than editing generated files.

- [ ] **Step 2: Replace any missed production JSON work with `JsonCodec`**

For each remaining non-approved production match, apply the same replacement pattern:

```java
private final JsonCodec jsonCodec;

public Example(JsonCodec jsonCodec) {
    this.jsonCodec = jsonCodec;
}
```

and:

```java
jsonCodec.toJson(value);
jsonCodec.fromJson(json, ValueType.class);
jsonCodec.readTree(json);
jsonCodec.treeToValue(node, ValueType.class);
jsonCodec.valueToTree(value);
```

Run the focused test for the touched class immediately after each replacement.

- [ ] **Step 3: Run affected module test suites**

Run:

```bash
cd backend
mvn test -pl :community-common-json,:community-common-core,:community-common-web,:community-common-webflux,:community-common-idempotency,:community-app,:im-core,:im-realtime,:community-im-gateway,:community-oss-client -am
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Run architecture tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit final cleanup**

If Step 2 changed files, commit them:

```bash
git add backend
git commit -m "refactor: complete json codec migration"
```

If Step 2 changed no files, do not create an empty commit.

## Self-Review Checklist

- Spec coverage: The plan creates `community-common-json`, removes Jackson from `common-core`, keeps DTO annotations, preserves mapper behavior, migrates explicit JSON work, removes `JsonNode` from notice domain, and adds guardrails.
- Placeholder scan: Complete; tasks contain concrete file paths, commands, and expected outcomes.
- Type consistency: The plan uses `JsonCodec`, `JacksonJsonCodec`, `JsonMappers`, `JsonCodecException`, and `EventEnvelopeJsonParser` consistently across tasks.
- Verification: Each task includes a Maven command and expected result before the commit step.
