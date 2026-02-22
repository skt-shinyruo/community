package com.nowcoder.community.user.api;

// 安全契约测试：锁定 user-service 的公开 GET 白名单与管理员接口鉴权语义，避免规则漂移。
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserSecurityContractTest {

    private static final String TEST_AVATAR_KEY = "avatar/1/0123456789abcdef0123456789abcdef";

    @Autowired
    MockMvc mockMvc;

    @Value("${security.jwt.hmac-secret}")
    private String hmacSecret;

    @Test
    void userProfileShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk());
    }

    @Test
    void filesShouldBePublic() throws Exception {
        // /files/** 在 local storage 模式下可能返回 404（文件不存在），但不应被 401/403 遮蔽。
        MvcResult r = mockMvc.perform(get("/files/" + TEST_AVATAR_KEY))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void adminApisShouldRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/users/admin/search").param("userId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/users/admin/search")
                        .param("userId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/users/admin/search")
                        .param("userId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(List.of("ROLE_ADMIN"))))
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

