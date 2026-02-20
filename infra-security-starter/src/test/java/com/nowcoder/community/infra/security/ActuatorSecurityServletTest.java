package com.nowcoder.community.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ActuatorSecurityServletTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "security.jwt.hmac-secret=01234567890123456789012345678901",
                "community.metrics.basic-auth.username=prometheus",
                "community.metrics.basic-auth.password=test-prometheus-pass-please-change",
                "management.endpoint.prometheus.enabled=true",
                "management.metrics.export.prometheus.enabled=true",
                "management.prometheus.metrics.export.enabled=true",
                "management.endpoints.web.exposure.include=health,info,prometheus,env"
        }
)
@AutoConfigureMockMvc
class ActuatorSecurityServletTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void uses_servlet_security_chain() {
        assertThat(applicationContext.containsBean("actuatorSecurityFilterChain")).isTrue();
        assertThat(applicationContext.containsBean("actuatorSecurityWebFilterChain")).isFalse();
    }

    @Test
    void health_and_info_are_public() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    void prometheus_requires_prometheus_role() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .with(httpBasic("prometheus", "test-prometheus-pass-please-change")))
                .andExpect(status().isOk());
    }

    @Test
    void other_actuator_endpoints_are_deny_all() throws Exception {
        mockMvc.perform(get("/actuator/env")
                        .with(httpBasic("prometheus", "test-prometheus-pass-please-change")))
                .andExpect(status().isForbidden());
    }

    @SpringBootApplication
    static class TestApp {
    }
}
