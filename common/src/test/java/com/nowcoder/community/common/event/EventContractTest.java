package com.nowcoder.community.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.trace.TraceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventContractTest {

    @AfterEach
    void tearDown() {
        TraceId.clear();
    }

    @Test
    void eventEnvelopeShouldContainRequiredFields() throws Exception {
        TraceId.set("t1");

        Map<String, Object> payload = new HashMap<>();
        payload.put("postId", 123);
        payload.put("userId", 1);
        payload.put("title", "hello");
        payload.put("content", "content");
        payload.put("type", 0);
        payload.put("status", 0);
        payload.put("createTime", Instant.parse("2026-01-16T06:28:00Z"));
        payload.put("score", 0.0);

        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.of("PostPublished", 1, "content-service", payload);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Map<?, ?> json = mapper.readValue(mapper.writeValueAsString(envelope), Map.class);

        assertThat(json.get("eventId")).isNotNull();
        assertThat(json.get("traceId")).isEqualTo("t1");
        assertThat(json.get("type")).isEqualTo("PostPublished");
        assertThat(json.get("version")).isEqualTo(1);
        assertThat(json.get("occurredAt")).isNotNull();
        assertThat(json.get("producer")).isEqualTo("content-service");
        assertThat(json.get("payload")).isInstanceOf(Map.class);

        Map<?, ?> p = (Map<?, ?>) json.get("payload");
        assertThat(p.get("postId")).isEqualTo(123);
        assertThat(p.get("userId")).isEqualTo(1);
        assertThat(p.get("title")).isEqualTo("hello");
    }

    @Test
    void eventPayloadShouldAvoidSensitiveFields() throws Exception {
        TraceId.set("t2");

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", 1);
        payload.put("entityType", 1);
        payload.put("entityId", 100);
        payload.put("entityUserId", 2);
        payload.put("postId", 100);
        payload.put("createTime", Instant.now());

        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.of("LikeCreated", 1, "social-service", payload);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(envelope);

        // 约束：事件中不应携带邮箱/密码等敏感字段
        assertThat(json).doesNotContain("email");
        assertThat(json).doesNotContain("password");
        assertThat(json).contains("LikeCreated");
    }

    @Test
    void commentCreatedPayloadShouldBeSerializable() throws Exception {
        TraceId.set("t3");

        Map<String, Object> payload = new HashMap<>();
        payload.put("commentId", 1);
        payload.put("postId", 100);
        payload.put("userId", 2);
        payload.put("entityType", 1);
        payload.put("entityId", 100);
        payload.put("targetUserId", 1);
        payload.put("content", "hi");
        payload.put("createTime", Instant.now());

        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.of("CommentCreated", 1, "content-service", payload);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(envelope);

        assertThat(json).contains("CommentCreated");
        assertThat(json).contains("payload");
    }

    @Test
    void commentDeletedPayloadShouldBeSerializable() throws Exception {
        TraceId.set("t4");

        Map<String, Object> payload = new HashMap<>();
        payload.put("commentId", 1);
        payload.put("postId", 100);
        payload.put("userId", 2);
        payload.put("entityType", 1);
        payload.put("entityId", 100);
        payload.put("targetUserId", 1);
        payload.put("content", null);
        payload.put("createTime", Instant.now());

        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.of("CommentDeleted", 1, "content-service", payload);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(envelope);

        assertThat(json).contains("CommentDeleted");
        assertThat(json).contains("payload");
    }
}
