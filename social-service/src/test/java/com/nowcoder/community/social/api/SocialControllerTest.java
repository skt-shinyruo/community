package com.nowcoder.community.social.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.common.domain.EntityTypes;
import com.nowcoder.community.social.service.ContentEntityResolver;
import com.nowcoder.community.social.follow.dto.FollowRequest;
import com.nowcoder.community.social.like.dto.LikeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.Mockito;

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
        "security.jwt.hmac-secret=test-jwt-secret-please-change-at-least-32bytes"
})
@AutoConfigureMockMvc
class SocialControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    ContentEntityResolver contentEntityResolver;

    @Test
    void likeApisShouldRequireAuth() throws Exception {
        mockMvc.perform(get("/api/likes/status").param("entityType", "1").param("entityId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void likeThenStatusShouldWork() throws Exception {
        String token = tokenForUser(1);

        Mockito.when(contentEntityResolver.resolve(1, 100)).thenReturn(new ContentEntityResolver.ResolvedEntity(2, 100));

        LikeRequest req = new LikeRequest();
        req.setEntityType(1);
        req.setEntityId(100);
        req.setEntityUserId(2);
        req.setLiked(true);

        mockMvc.perform(post("/api/likes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/likes/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("entityType", "1")
                        .param("entityId", "100"))
                .andExpect(status().isOk());
    }

    @Test
    void followShouldReturnDomainCodeWhenFollowSelf() throws Exception {
        String token = tokenForUser(1);

        FollowRequest req = new FollowRequest();
        req.setEntityType(EntityTypes.USER);
        req.setEntityId(1);
        req.setEntityUserId(1);

        mockMvc.perform(post("/api/follows")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(13001))
                .andExpect(jsonPath("$.traceId").exists());
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
