package com.nowcoder.community.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.auth.api.dto.RegisterRequest;
import com.nowcoder.community.auth.service.CaptchaStore;
import com.nowcoder.community.auth.api.dto.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
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
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CaptchaStore captchaStore;

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
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginShouldBeRateLimitedAfterRepeatedFailures() throws Exception {
        String ip = "203.0.113.77";
        LoginRequest req = new LoginRequest();
        // 使用一个不存在的账号，避免影响其他测试用例中的正常登录
        req.setUsername("no_such_user");
        req.setPassword("wrong");
        String body = objectMapper.writeValueAsString(req);

        // 前 4 次失败仍返回业务错误（HTTP 200 + code=10001）；第 5 次触发 429
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());
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
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("\"code\":10007"));
    }
}
