package com.nowcoder.community.message.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NoticeEventConsumerIntegrationTest {

    @Autowired
    NoticeEventProcessor processor;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MockMvc mockMvc;

    @Test
    void consumeEventThenQueryNoticeShouldWork() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "e1",
                "type", EventTypes.LIKE_CREATED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "social-service",
                "payload", Map.of(
                        "actorUserId", 2,
                        "entityType", 1,
                        "entityId", 100,
                        "entityUserId", 42,
                        "postId", 100,
                        "createTime", Instant.now().toString()
                )
        ));

        // 重复投递/重试场景：同 eventId 不应产生重复通知
        processor.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 0L, "k1", payload));
        processor.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 1L, "k1", payload));

        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject("42")
                        .issueTime(java.util.Date.from(Instant.now()))
                        .expirationTime(java.util.Date.from(Instant.now().plusSeconds(600)))
                        .claim("username", "u42")
                        .claim("authorities", List.of("ROLE_USER"))
                        .build()
        );
        jwt.sign(new MACSigner("test-jwt-secret-please-change-at-least-32bytes".getBytes(StandardCharsets.UTF_8)));
        String token = jwt.serialize();

        String resp = mockMvc.perform(get("/api/notices")
                        .param("topic", "like")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(resp).contains("e1");
        assertThat(resp.split("e1", -1).length - 1).isEqualTo(1);
    }
}
