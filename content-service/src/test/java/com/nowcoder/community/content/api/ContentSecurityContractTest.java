package com.nowcoder.community.content.api;

// 安全契约测试：锁定 content-service 的公开 GET 白名单与治理/管理接口的鉴权语义，避免规则漂移。
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContentSecurityContractTest {

    @Autowired
    MockMvc mockMvc;

    @Value("${security.jwt.hmac-secret}")
    private String hmacSecret;

    @Test
    void categoriesAndTagsShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/tags/hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/tags/suggest").param("q", "a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void moderationApisShouldRequireModeratorOrAdminRole() throws Exception {
        mockMvc.perform(get("/api/moderation/__test__"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/moderation/__test__")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/moderation/__test__")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_MODERATOR"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void postGovernanceApisShouldRequireModeratorOrAdminRole() throws Exception {
        mockMvc.perform(post("/api/posts/1/top"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(post("/api/posts/1/top")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.traceId").exists());

        MvcResult r = mockMvc.perform(post("/api/posts/1/top")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_MODERATOR"))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isNotIn(401, 403);
    }

    private String tokenWithAuthorities(List<String> authorities) {
        try {
            byte[] secretBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
            JWSSigner signer = new MACSigner(secretBytes);

            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("1")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(3600)))
                    .claim("authorities", authorities)
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to build test JWT", e);
        }
    }
}

