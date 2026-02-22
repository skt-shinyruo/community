package com.nowcoder.community.social.api;

// 安全契约测试：锁定 social-service 的公开读接口白名单与写/状态接口的鉴权语义，避免规则漂移。
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SocialSecurityContractTest {

    @Autowired
    MockMvc mockMvc;

    @Value("${security.jwt.hmac-secret}")
    private String hmacSecret;

    @Test
    void publicReadApisShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/likes/count").param("entityType", "1").param("entityId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/likes/counts").param("entityType", "1").param("entityIds", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/likes/users/1/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/follows/1/followees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/follows/1/followers/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void nonPublicApisShouldRequireAuth() throws Exception {
        mockMvc.perform(get("/api/likes/status").param("entityType", "1").param("entityId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/likes/status")
                        .param("entityType", "1")
                        .param("entityId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").exists());
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

