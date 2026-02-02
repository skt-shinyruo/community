package com.nowcoder.community.social.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.social.follow.dto.FollowRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "social.storage=memory",
        "social.events.publisher=memory",
        "security.jwt.hmac-secret=test-jwt-secret-please-change-at-least-32bytes",
        "social.internal-token=test-internal-token"
})
@AutoConfigureMockMvc
class InternalSocialReadControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void internalProfileStatsShouldRequireInternalToken() throws Exception {
        mockMvc.perform(get("/internal/social/read/users/1/profile-stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalProfileStatsShouldAggregateCountsAndStatus() throws Exception {
        String viewerToken = tokenForUser(1);

        FollowRequest follow = new FollowRequest();
        follow.setEntityType(3);
        follow.setEntityId(2);

        mockMvc.perform(post("/api/follows")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(follow)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/internal/social/read/users/2/profile-stats")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("viewerId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followerCount").value(1))
                .andExpect(jsonPath("$.data.followeeCount").value(0))
                .andExpect(jsonPath("$.data.hasFollowed").value(true))
                .andExpect(jsonPath("$.data.degraded").value(false));
    }

    private String tokenForUser(int userId) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject(String.valueOf(userId))
                        .issueTime(java.util.Date.from(Instant.now()))
                        .expirationTime(java.util.Date.from(Instant.now().plusSeconds(600)))
                        .claim("username", "u" + userId)
                        .claim("authorities", List.of("ROLE_USER"))
                        .build()
        );
        jwt.sign(new MACSigner("test-jwt-secret-please-change-at-least-32bytes".getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}

