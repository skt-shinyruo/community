package com.nowcoder.community.analytics.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InternalAnalyticsControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void internalAnalyticsShouldRequireInternalToken() throws Exception {
        mockMvc.perform(post("/internal/analytics/uv/record")
                        .param("ip", "1.1.1.1")
                        .param("date", "2026-01-01"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void internalAnalyticsShouldReturn400OnMissingParamWithToken() throws Exception {
        mockMvc.perform(post("/internal/analytics/uv/record")
                        .header("X-Internal-Token", "test-internal")
                        .param("date", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.traceId").exists());
    }
}

