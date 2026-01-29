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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerSecurityTest {

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
    void nonMemberShouldNotReadConversationLetters() throws Exception {
        jdbcTemplate.update(
                "insert into message(id, from_id, to_id, conversation_id, content, status, create_time) values (?,?,?,?,?,?,?)",
                100, 2, 3, "2_3", "hi", 0, Timestamp.from(Instant.now())
        );

        mockMvc.perform(
                        get("/api/messages/conversations/2_3")
                                .header("Authorization", "Bearer " + jwtToken(4))
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(
                        get("/api/messages/conversations/2_3")
                                .header("Authorization", "Bearer " + jwtToken(2))
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(100));
    }

    @Test
    void markReadShouldNotUpdateOtherUsersMessages() throws Exception {
        jdbcTemplate.update(
                "insert into message(id, from_id, to_id, conversation_id, content, status, create_time) values (?,?,?,?,?,?,?)",
                200, 9, 2, "2_9", "m1", 0, Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                "insert into message(id, from_id, to_id, conversation_id, content, status, create_time) values (?,?,?,?,?,?,?)",
                201, 9, 3, "3_9", "m2", 0, Timestamp.from(Instant.now())
        );

        String body = objectMapper.writeValueAsString(new MarkReadBody(List.of(200, 201)));
        mockMvc.perform(
                        put("/api/messages/read")
                                .header("Authorization", "Bearer " + jwtToken(2))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Integer s200 = jdbcTemplate.queryForObject("select status from message where id=200", Integer.class);
        Integer s201 = jdbcTemplate.queryForObject("select status from message where id=201", Integer.class);
        assertThat(s200).isEqualTo(1);
        assertThat(s201).isEqualTo(0);
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

