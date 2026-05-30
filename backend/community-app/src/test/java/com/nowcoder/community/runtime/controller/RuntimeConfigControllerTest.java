package com.nowcoder.community.runtime.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.runtime.application.RuntimeConfigApplicationService;
import com.nowcoder.community.runtime.application.result.RuntimeConfigResult;
import com.nowcoder.community.runtime.security.RuntimeSecurityRules;
import com.nowcoder.community.support.WebMvcSliceJsonCodecTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuntimeConfigController.class)
@Import({
        RuntimeConfigController.class,
        RuntimeSecurityRules.class,
        CommunitySecurityConfig.class,
        WebMvcSliceJsonCodecTestConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false"
})
class RuntimeConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuntimeConfigApplicationService applicationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void runtimeConfigShouldBeAvailableBeforeAuthentication() throws Exception {
        when(applicationService.current()).thenReturn(new RuntimeConfigResult(
                "/api",
                "http://localhost:12880",
                "ws://localhost:12880/ws/im",
                false,
                0.0,
                "local",
                Map.of("file-upload", true),
                new RuntimeConfigResult.UploadPolicy(
                        "10GB",
                        "10GB",
                        java.util.List.of("image/png"),
                        java.util.List.of("png"),
                        true,
                        true
                )
        ));

        mockMvc.perform(get("/api/runtime-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiBasePath").value("/api"))
                .andExpect(jsonPath("$.publicGatewayOrigin").value("http://localhost:12880"))
                .andExpect(jsonPath("$.websocketUrl").value("ws://localhost:12880/ws/im"))
                .andExpect(jsonPath("$.analyticsEnabled").value(false))
                .andExpect(jsonPath("$.analyticsSampleRate").value(0.0))
                .andExpect(jsonPath("$.releaseChannel").value("local"))
                .andExpect(jsonPath("$.features.file-upload").value(true))
                .andExpect(jsonPath("$.upload.maxFileSize").value("10GB"))
                .andExpect(jsonPath("$.upload.maxRequestSize").value("10GB"))
                .andExpect(jsonPath("$.upload.allowedMimeTypes[0]").value("image/png"))
                .andExpect(jsonPath("$.upload.allowedExtensions[0]").value("png"))
                .andExpect(jsonPath("$.upload.avatarUploadEnabled").value(true))
                .andExpect(jsonPath("$.upload.mediaUploadEnabled").value(true));
    }
}
