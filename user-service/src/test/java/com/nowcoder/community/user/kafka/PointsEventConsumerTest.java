package com.nowcoder.community.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import com.nowcoder.community.user.dao.UserMapper;
import com.nowcoder.community.user.entity.User;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "security.jwt.hmac-secret=test-jwt-secret-please-change-at-least-32bytes",
        "spring.datasource.url=jdbc:h2:mem:user;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql",
        "spring.kafka.listener.auto-startup=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
})
class PointsEventConsumerTest {

    @Autowired
    PointsEventConsumer consumer;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserMapper userMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDb() {
        jdbcTemplate.update("delete from user_score_log");
        jdbcTemplate.update("update user set score = 0");
    }

    @Test
    void postPublishedShouldAddPointsIdempotently() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "p1",
                "type", EventTypes.POST_PUBLISHED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "content-service",
                "payload", Map.of(
                        "postId", 100,
                        "userId", 1,
                        "title", "t",
                        "content", "c"
                )
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.POST_EVENTS_V1, 0, 0L, "k1", payload));
        consumer.handleRecord(new ConsumerRecord<>(EventTopics.POST_EVENTS_V1, 0, 1L, "k1", payload));

        User u1 = userMapper.selectById(1);
        assertThat(u1.getScore()).isEqualTo(10);
    }

    @Test
    void likeCreatedShouldAddPointsToEntityUserId() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "l1",
                "type", EventTypes.LIKE_CREATED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "social-service",
                "payload", Map.of(
                        "actorUserId", 2,
                        "entityType", 1,
                        "entityId", 100,
                        "entityUserId", 1,
                        "postId", 100,
                        "createTime", Instant.now().toString()
                )
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 0L, "k1", payload));
        consumer.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 1L, "k1", payload));

        User u1 = userMapper.selectById(1);
        assertThat(u1.getScore()).isEqualTo(1);
    }

    @Test
    void selfLikeShouldNotAddPoints() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "l2",
                "type", EventTypes.LIKE_CREATED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "social-service",
                "payload", Map.of(
                        "actorUserId", 1,
                        "entityType", 1,
                        "entityId", 100,
                        "entityUserId", 1,
                        "postId", 100,
                        "createTime", Instant.now().toString()
                )
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 0L, "k1", payload));

        User u1 = userMapper.selectById(1);
        assertThat(u1.getScore()).isEqualTo(0);
    }
}
