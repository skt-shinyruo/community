package com.nowcoder.community.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 最小冒烟：验证 auth-service 引入 infra-security-starter 后，actuator 安全策略与全站保持一致。
 */
@SpringBootTest(properties = {
        "management.endpoint.prometheus.enabled=true",
        "management.metrics.export.prometheus.enabled=true",
        "management.prometheus.metrics.export.enabled=true",
        "management.endpoints.web.exposure.include=health,info,prometheus"
})
@AutoConfigureMockMvc
class AuthActuatorSecuritySmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealth_shouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorPrometheus_shouldRequireBasicAuth() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("prometheus", "test-prometheus-pass-please-change")))
                .andExpect(status().isOk());
    }

    private static String basicAuth(String username, String password) {
        String raw = username + ":" + password;
        String token = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
