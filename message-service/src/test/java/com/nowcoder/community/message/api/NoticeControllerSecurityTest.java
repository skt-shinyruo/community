package com.nowcoder.community.message.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NoticeControllerSecurityTest {

    private static final String HMAC_SECRET = "test-jwt-secret-please-change-at-least-32bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("delete from message");
    }

    @Test
    void markReadShouldNotUpdateOtherUsersNotices() throws Exception {
        jdbcTemplate.update(
                "insert into message(id, from_id, to_id, conversation_id, content, status, create_time) values (?,?,?,?,?,?,?)",
                300, 1, 2, "comment", "n1", 0, Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                "insert into message(id, from_id, to_id, conversation_id, content, status, create_time) values (?,?,?,?,?,?,?)",
                301, 1, 3, "comment", "n2", 0, Timestamp.from(Instant.now())
        );

        String body = objectMapper.writeValueAsString(new MarkReadBody(List.of(300, 301)));
        mockMvc.perform(
                        put("/api/notices/read")
                                .header("Authorization", "Bearer " + jwtToken(2))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Integer s300 = jdbcTemplate.queryForObject("select status from message where id=300", Integer.class);
        Integer s301 = jdbcTemplate.queryForObject("select status from message where id=301", Integer.class);
        assertThat(s300).isEqualTo(1);
        assertThat(s301).isEqualTo(0);
    }

    private String jwtToken(int userId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(Integer.toString(userId))
                .claim("username", "u" + userId)
                .claim("authorities", List.of("ROLE_USER"))
                .issueTime(java.util.Date.from(now))
                .expirationTime(java.util.Date.from(now.plusSeconds(3600)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(HMAC_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private record MarkReadBody(List<Integer> ids) {
    }
}

