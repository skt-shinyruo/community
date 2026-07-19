package com.nowcoder.community.oss.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.security.autoconfig.SecurityCommonAutoConfiguration;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = OssSecurityConfigTest.SecurityProbeController.class,
        properties = {
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "security.jwt.hmac-secret=01234567890123456789012345678901",
                "security.jwt.issuer=community-auth",
                "oss.security.service-jwt.issuer=community-auth",
                "oss.security.service-jwt.audience=community-oss",
                "oss.security.service-jwt.scope=oss.internal"
        }
)
@Import({
        OssSecurityConfigTest.SecurityProbeController.class,
        OssSecurityConfig.class,
        SecurityCommonAutoConfiguration.class,
        SecurityExceptionHandler.class,
        OssMetricsSecurityWithoutCredentialsTest.WebMvcSliceJsonCodecTestConfig.class
})
class OssMetricsSecurityWithoutCredentialsTest {

    @Autowired
    private MockMvc mvc;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class WebMvcSliceJsonCodecTestConfig {

        @Bean
        JsonCodec jsonCodec(ObjectMapper objectMapper) {
            return new JacksonJsonCodec(objectMapper);
        }
    }

    @Test
    void missingMetricsPasswordShouldKeepApplicationOperableAndDenyPrometheus() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }
}
