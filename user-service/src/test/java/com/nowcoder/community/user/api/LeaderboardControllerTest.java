package com.nowcoder.community.user.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@AutoConfigureMockMvc
class LeaderboardControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void leaderboardShouldOrderByScoreDesc() throws Exception {
        jdbcTemplate.update("insert into user(id, username, score) values(2, 'u2', 120)");
        jdbcTemplate.update("insert into user(id, username, score) values(3, 'u3', 80)");
        jdbcTemplate.update("insert into user(id, username, score) values(4, 'u4', 120)");

        String resp = mockMvc.perform(get("/api/users/leaderboard").param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> body = objectMapper.readValue(resp, Map.class);
        List<?> items = (List<?>) body.get("data");
        assertThat(items).isNotNull();

        // 期望：score desc，其次 id asc
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        Map<?, ?> second = (Map<?, ?>) items.get(1);
        Map<?, ?> third = (Map<?, ?>) items.get(2);

        assertThat(((Number) first.get("userId")).intValue()).isEqualTo(2);
        assertThat(((Number) second.get("userId")).intValue()).isEqualTo(4);
        assertThat(((Number) third.get("userId")).intValue()).isEqualTo(3);
    }
}

