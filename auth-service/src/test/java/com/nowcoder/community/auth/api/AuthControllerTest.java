package com.nowcoder.community.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.auth.api.dto.RegisterRequest;
import com.nowcoder.community.auth.service.CaptchaStore;
import com.nowcoder.community.auth.api.dto.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.auth.service.UserServiceInternalClient;
import com.nowcoder.community.common.api.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.rpc.dto.UserInternalAuthenticateResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalRefreshTokenRecordResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalRegisterResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalSessionProfileResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalUserByEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthControllerTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // 让 MockMvc 的 X-Forwarded-For 在测试中生效，避免所有请求落到 127.0.0.1 导致风控/限流状态跨用例污染
        registry.add("gateway.trusted-proxy.enabled", () -> "true");
        registry.add("gateway.trusted-proxy.cidrs", () -> "127.0.0.1/32");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CaptchaStore captchaStore;

    @MockBean
    UserServiceInternalClient userServiceInternalClient;

    private final Map<String, TestUser> usersByUsername = new ConcurrentHashMap<>();
    private final Map<Integer, TestUser> usersById = new ConcurrentHashMap<>();
    private final Map<String, Integer> userIdByEmail = new ConcurrentHashMap<>();
    private final Map<String, UserInternalRefreshTokenRecordResponse> refreshTokensByHash = new ConcurrentHashMap<>();
    private final AtomicInteger userIdSeq = new AtomicInteger(1000);

    @BeforeEach
    void setupUserServiceStubs() {
        usersByUsername.clear();
        usersById.clear();
        userIdByEmail.clear();
        refreshTokensByHash.clear();

        // 预置用户（对齐 deploy/mysql-init/090_seed_identity.sql 的默认账号）
        TestUser aaa = new TestUser(1, "aaa", "aaa", "aaa@example.com", 1, List.of("ROLE_USER"));
        usersByUsername.put(aaa.username, aaa);
        usersById.put(aaa.userId, aaa);
        userIdByEmail.put(aaa.email, aaa.userId);

        when(userServiceInternalClient.authenticate(anyString(), anyString())).thenAnswer(invocation -> {
            String username = invocation.getArgument(0);
            String password = invocation.getArgument(1);
            TestUser user = usersByUsername.get(username);
            if (user == null || user.userId <= 0) {
                throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
            }
            if (user.status == 0) {
                throw new BusinessException(AuthErrorCode.USER_DISABLED);
            }
            if (!user.password.equals(password)) {
                throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
            }
            UserInternalAuthenticateResponse resp = new UserInternalAuthenticateResponse();
            resp.setUserId(user.userId);
            resp.setUsername(user.username);
            resp.setStatus(user.status);
            resp.setAuthorities(user.authorities);
            return resp;
        });

        when(userServiceInternalClient.sessionProfile(anyInt())).thenAnswer(invocation -> {
            int userId = invocation.getArgument(0);
            TestUser user = usersById.get(userId);
            if (user == null) {
                throw new BusinessException(AuthErrorCode.USER_DISABLED);
            }
            UserInternalSessionProfileResponse resp = new UserInternalSessionProfileResponse();
            resp.setUserId(user.userId);
            resp.setUsername(user.username);
            resp.setStatus(user.status);
            resp.setAuthorities(user.authorities);
            return resp;
        });

        when(userServiceInternalClient.register(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String username = invocation.getArgument(0);
            String password = invocation.getArgument(1);
            String email = invocation.getArgument(2);

            if (usersByUsername.containsKey(username)) {
                throw new BusinessException(AuthErrorCode.LOGIN_FAILED, "该账号已存在");
            }
            if (userIdByEmail.containsKey(email)) {
                throw new BusinessException(AuthErrorCode.LOGIN_FAILED, "该邮箱已被注册");
            }

            int userId = userIdSeq.incrementAndGet();
            String activationCode = "ac-" + userId;

            TestUser user = new TestUser(userId, username, password, email, 0, List.of("ROLE_USER"));
            user.activationCode = activationCode;
            usersByUsername.put(username, user);
            usersById.put(userId, user);
            userIdByEmail.put(email, userId);

            UserInternalRegisterResponse resp = new UserInternalRegisterResponse();
            resp.setUserId(userId);
            resp.setActivationCode(activationCode);
            return resp;
        });

        when(userServiceInternalClient.activate(anyInt(), anyString())).thenAnswer(invocation -> {
            int userId = invocation.getArgument(0);
            String code = invocation.getArgument(1);
            TestUser user = usersById.get(userId);
            if (user == null) {
                return 2;
            }
            if (user.status == 1) {
                return 1;
            }
            if (user.activationCode != null && user.activationCode.equals(code)) {
                user.status = 1;
                return 0;
            }
            return 2;
        });

        when(userServiceInternalClient.findByEmailOrNull(anyString())).thenAnswer(invocation -> {
            String email = invocation.getArgument(0);
            Integer userId = userIdByEmail.get(email);
            if (userId == null) {
                return null;
            }
            TestUser user = usersById.get(userId);
            if (user == null) {
                return null;
            }
            UserInternalUserByEmailResponse resp = new UserInternalUserByEmailResponse();
            resp.setUserId(user.userId);
            resp.setUsername(user.username);
            resp.setEmail(user.email);
            resp.setStatus(user.status);
            return resp;
        });

        doAnswer(invocation -> {
            int userId = invocation.getArgument(0);
            String newPassword = invocation.getArgument(1);
            TestUser user = usersById.get(userId);
            if (user != null) {
                user.password = newPassword;
            }
            return null;
        }).when(userServiceInternalClient).updatePassword(anyInt(), anyString());

        doAnswer(invocation -> {
            String tokenHash = invocation.getArgument(0);
            int userId = invocation.getArgument(1);
            String familyId = invocation.getArgument(2);
            Instant expiresAt = invocation.getArgument(3);
            UserInternalRefreshTokenRecordResponse r = new UserInternalRefreshTokenRecordResponse();
            r.setTokenHash(tokenHash);
            r.setUserId(userId);
            r.setFamilyId(familyId);
            r.setExpiresAt(expiresAt);
            r.setRevokedAt(null);
            refreshTokensByHash.put(tokenHash, r);
            return null;
        }).when(userServiceInternalClient).storeRefreshToken(anyString(), anyInt(), anyString(), any());

        when(userServiceInternalClient.findRefreshTokenOrNull(anyString())).thenAnswer(invocation -> {
            String tokenHash = invocation.getArgument(0);
            return refreshTokensByHash.get(tokenHash);
        });

        doAnswer(invocation -> {
            String tokenHash = invocation.getArgument(0);
            UserInternalRefreshTokenRecordResponse r = refreshTokensByHash.get(tokenHash);
            if (r != null && r.getRevokedAt() == null) {
                r.setRevokedAt(Instant.now());
            }
            return null;
        }).when(userServiceInternalClient).revokeRefreshToken(anyString());

        doAnswer(invocation -> {
            String familyId = invocation.getArgument(0);
            for (UserInternalRefreshTokenRecordResponse r : refreshTokensByHash.values()) {
                if (r == null) {
                    continue;
                }
                if (familyId != null && familyId.equals(r.getFamilyId()) && r.getRevokedAt() == null) {
                    r.setRevokedAt(Instant.now());
                }
            }
            return null;
        }).when(userServiceInternalClient).revokeRefreshTokenFamily(anyString());
    }

    @Test
    void loginRefreshLogoutFlowShouldWork() throws Exception {
        String ip = "198.51.100.11";
        LoginRequest req = new LoginRequest();
        req.setUsername("aaa");
        req.setPassword("aaa");

        String loginJson = objectMapper.writeValueAsString(req);

        String loginResp = mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(loginResp).contains("\"accessToken\"");

        String refreshResp = mockMvc.perform(post("/api/auth/refresh")
                .cookie(mockMvc.perform(post("/api/auth/login")
                                        .header("X-Forwarded-For", ip)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginJson))
                                .andReturn()
                                .getResponse()
                                .getCookies()))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(refreshResp).contains("\"accessToken\"");
    }

    @Test
    void meShouldRequireAuth() throws Exception {
        String body = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.path("code").asInt()).isEqualTo(401);
        assertThat(json.path("traceId").asText()).isNotBlank();
    }

    @Test
    void loginShouldBeRateLimitedAfterRepeatedFailures() throws Exception {
        String ip = "203.0.113.77";
        LoginRequest req = new LoginRequest();
        // 使用一个不存在的账号，避免影响其他测试用例中的正常登录
        req.setUsername("no_such_user");
        req.setPassword("wrong");
        String body = objectMapper.writeValueAsString(req);

        // 前 2 次失败：LOGIN_FAILED（HTTP 401 + code=10001）
        // 之后触发验证码要求：CAPTCHA_REQUIRED（HTTP 400 + code=10005）
        // 第 5 次达到阈值：TOO_MANY_REQUESTS（HTTP 429）
        for (int i = 0; i < 4; i++) {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(i < 2 ? status().isUnauthorized() : status().isBadRequest())
                    .andReturn();
            JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
            int code = json.path("code").asInt();
            assertThat(json.path("traceId").asText()).isNotBlank();
            assertThat(code).isEqualTo(i < 2 ? AuthErrorCode.LOGIN_FAILED.getCode() : AuthErrorCode.CAPTCHA_REQUIRED.getCode());
        }

        MvcResult limited = mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andReturn();
        JsonNode limitedJson = objectMapper.readTree(limited.getResponse().getContentAsString());
        int limitedCode = limitedJson.path("code").asInt();
        assertThat(limitedJson.path("traceId").asText()).isNotBlank();
        assertThat(limitedCode).isEqualTo(429);
    }

    @Test
    void loginShouldRejectBadJsonWith400() throws Exception {
        String ip = "203.0.113.88";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.path("code").asInt()).isEqualTo(400);
        assertThat(json.path("traceId").asText()).isNotBlank();
    }

    @Test
    void registerActivateLoginFlowShouldWork() throws Exception {
        String ip = "198.51.100.22";
        // 注册前先获取验证码（captchaId），并使用存储中的答案完成校验
        String issuedJson = mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode issued = objectMapper.readTree(issuedJson);
        String captchaId = issued.path("data").path("captchaId").asText();
        assertThat(captchaId).isNotBlank();
        String captchaCode = captchaStore.get(captchaId);
        assertThat(captchaCode).isNotBlank();

        RegisterRequest req = new RegisterRequest();
        req.setUsername("new_user");
        req.setPassword("pwd");
        req.setEmail("new_user@example.com");
        req.setCaptchaId(captchaId);
        req.setCaptchaCode(captchaCode);

        String respJson = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(respJson).contains("\"userId\"");
        assertThat(respJson).contains("\"activationLink\"");

        int userId = objectMapper.readTree(respJson).path("data").path("userId").asInt();
        String activationLink = objectMapper.readTree(respJson).path("data").path("activationLink").asText();
        assertThat(userId).isPositive();
        assertThat(activationLink).contains("/api/auth/activation/" + userId + "/");

        String code = activationLink.substring(activationLink.lastIndexOf("/") + 1);

        mockMvc.perform(get("/api/auth/activation/" + userId + "/" + code))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("\"data\":0"));

        LoginRequest login = new LoginRequest();
        login.setUsername("new_user");
        login.setPassword("pwd");

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void captchaIssueAndVerifyShouldWork() throws Exception {
        MvcResult issue = mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode issued = objectMapper.readTree(issue.getResponse().getContentAsString());
        String captchaId = issued.path("data").path("captchaId").asText();
        assertThat(captchaId).isNotBlank();

        String code = captchaStore.get(captchaId);
        assertThat(code).isNotBlank();

        mockMvc.perform(post("/api/auth/captcha/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"captchaId\":\"" + captchaId + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("\"data\":true"));

        // one-time token: second verify should fail
        mockMvc.perform(post("/api/auth/captcha/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"captchaId\":\"" + captchaId + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("\"data\":false"));

        // 失败次数阈值：同一 captcha 连续失败 3 次后作废
        String issued2Json = mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode issued2 = objectMapper.readTree(issued2Json);
        String captchaId2 = issued2.path("data").path("captchaId").asText();
        assertThat(captchaId2).isNotBlank();
        assertThat(captchaStore.get(captchaId2)).isNotBlank();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/captcha/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"captchaId\":\"" + captchaId2 + "\",\"code\":\"0000\"}"))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("\"data\":false"));
        }
        assertThat(captchaStore.get(captchaId2)).isNull();
    }

    @Test
    void passwordResetShouldWorkAndAvoidUserEnumeration() throws Exception {
        // 已存在用户：应返回 issued=true，并在测试配置下回传 resetLink（用于联调）
        String capJson = mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode cap = objectMapper.readTree(capJson);
        String captchaId = cap.path("data").path("captchaId").asText();
        assertThat(captchaId).isNotBlank();
        String captchaCode = captchaStore.get(captchaId);
        assertThat(captchaCode).isNotBlank();

        String respJson = mockMvc.perform(post("/api/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"aaa@example.com\",\"captchaId\":\"" + captchaId + "\",\"captchaCode\":\"" + captchaCode + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = objectMapper.readTree(respJson);
        assertThat(body.path("data").path("issued").asBoolean()).isTrue();
        assertThat(body.path("data").path("resetLink").asText()).isNotBlank();

        // 不存在邮箱：仍返回 issued=true，但不回传 resetLink（避免用户枚举）
        String capJson2 = mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode cap2 = objectMapper.readTree(capJson2);
        String captchaId2 = cap2.path("data").path("captchaId").asText();
        assertThat(captchaId2).isNotBlank();
        String captchaCode2 = captchaStore.get(captchaId2);
        assertThat(captchaCode2).isNotBlank();

        String respJson2 = mockMvc.perform(post("/api/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no_such_email@example.com\",\"captchaId\":\"" + captchaId2 + "\",\"captchaCode\":\"" + captchaCode2 + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body2 = objectMapper.readTree(respJson2);
        assertThat(body2.path("data").path("issued").asBoolean()).isTrue();
        assertThat(body2.path("data").path("resetLink").asText()).isBlank();

        // confirm：无效 token 应返回业务错误码（不应 500）
        String capJson3 = mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode cap3 = objectMapper.readTree(capJson3);
        String captchaId3 = cap3.path("data").path("captchaId").asText();
        assertThat(captchaId3).isNotBlank();
        String captchaCode3 = captchaStore.get(captchaId3);
        assertThat(captchaCode3).isNotBlank();

        mockMvc.perform(post("/api/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"bad-token\",\"newPassword\":\"new_pwd\",\"captchaId\":\"" + captchaId3 + "\",\"captchaCode\":\"" + captchaCode3 + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("\"code\":10007"));
    }

    private static class TestUser {
        private final int userId;
        private final String username;
        private String password;
        private final String email;
        private int status;
        private final List<String> authorities;
        private String activationCode;

        private TestUser(int userId, String username, String password, String email, int status, List<String> authorities) {
            this.userId = userId;
            this.username = username;
            this.password = password;
            this.email = email;
            this.status = status;
            this.authorities = authorities;
        }
    }
}
