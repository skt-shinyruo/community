package com.nowcoder.community.user.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nowcoder.community.user.api.dto.UpdateAvatarRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "security.jwt.hmac-secret=test-jwt-secret-please-change-at-least-32bytes",
        "qiniu.key.access=test-ak",
        "qiniu.key.secret=test-sk",
        "qiniu.bucket.header.name=test-bucket",
        "qiniu.bucket.header.url=http://bucket.local",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
})
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    StringRedisTemplate redisTemplate;

    private final Map<String, String> redis = new ConcurrentHashMap<>();

    @BeforeEach
    void setupRedisMock() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            redis.put(key, value);
            return null;
        }).when(ops).set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));

        when(ops.getAndDelete(anyString())).thenAnswer(invocation -> redis.remove(invocation.getArgument(0)));
        when(ops.get(anyString())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
    }

    @Test
    void getUserShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk());
    }

    @Test
    void uploadTokenAndUpdateAvatarShouldRequireAuth() throws Exception {
        mockMvc.perform(get("/api/users/1/avatar/upload-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/1/avatar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateAvatarThenGetUserShouldReturnNewHeaderUrl() throws Exception {
        String token = tokenForUser(1);

        String uploadJson = mockMvc.perform(get("/api/users/1/avatar/upload-token")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String fileName = objectMapper.readTree(uploadJson).path("data").path("fileName").asText();
        assertThat(fileName).startsWith("avatar/");

        UpdateAvatarRequest req = new UpdateAvatarRequest();
        req.setFileName(fileName);

        mockMvc.perform(put("/api/users/1/avatar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        String userJson = mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String headerUrl = objectMapper.readTree(userJson).path("data").path("headerUrl").asText();
        assertThat(headerUrl).isEqualTo("http://bucket.local/" + fileName);
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
