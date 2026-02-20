package com.nowcoder.community.ops.api;

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
class OpsSecurityContractTest {

    @Autowired
    MockMvc mockMvc;

    @Value("${security.jwt.hmac-secret}")
    private String hmacSecret;

    @Test
    void opsPathsShouldRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/ops/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/ops/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.traceId").exists());

        // ROLE_ADMIN：通过安全链路，但因为 endpoint 不存在返回 404（用于验证仅靠安全链路即可约束访问）
        mockMvc.perform(get("/api/ops/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
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

