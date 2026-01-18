package com.nowcoder.community.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.payload.CommentPayload;
import com.nowcoder.community.common.event.payload.LikePayload;
import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.common.trace.TraceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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

        PostPayload payload = new PostPayload();
        payload.setPostId(123);
        payload.setUserId(1);
        payload.setTitle("hello");
        payload.setContent("content");
        payload.setType(0);
        payload.setStatus(0);
        payload.setCreateTime(Instant.parse("2026-01-16T06:28:00Z"));
        payload.setScore(0.0);

        EventEnvelope<PostPayload> envelope = EventEnvelope.of(EventTypes.POST_PUBLISHED, 1, "content-service", payload);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Map<?, ?> json = mapper.readValue(mapper.writeValueAsString(envelope), Map.class);

        assertThat(json.get("eventId")).isNotNull();
        assertThat(json.get("traceId")).isEqualTo("t1");
        assertThat(json.get("type")).isEqualTo(EventTypes.POST_PUBLISHED);
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

        LikePayload payload = new LikePayload();
        payload.setActorUserId(1);
        payload.setEntityType(1);
        payload.setEntityId(100);
        payload.setEntityUserId(2);
        payload.setPostId(100);
        payload.setCreateTime(Instant.now());

        EventEnvelope<LikePayload> envelope = EventEnvelope.of(EventTypes.LIKE_CREATED, 1, "social-service", payload);

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

        CommentPayload payload = new CommentPayload();
        payload.setCommentId(1);
        payload.setPostId(100);
        payload.setUserId(2);
        payload.setEntityType(1);
        payload.setEntityId(100);
        payload.setTargetUserId(1);
        payload.setContent("hi");
        payload.setCreateTime(Instant.now());

        EventEnvelope<CommentPayload> envelope = EventEnvelope.of(EventTypes.COMMENT_CREATED, 1, "content-service", payload);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(envelope);

        assertThat(json).contains("CommentCreated");
        assertThat(json).contains("payload");
    }
}
