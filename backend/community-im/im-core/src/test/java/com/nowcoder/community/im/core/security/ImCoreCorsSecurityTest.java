package com.nowcoder.community.im.core.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImCoreCorsSecurityTest {

    @Test
    void corsConfigurationShouldExposeOnlyConfiguredOriginsAndMethods() {
        ImCoreCorsProperties properties = new ImCoreCorsProperties();
        properties.setAllowedOrigins(List.of(
                "http://localhost:12881",
                "http://127.0.0.1:12881"
        ));

        CorsConfigurationSource source = new ImCoreSecurityConfig().corsConfigurationSource(properties);

        CorsConfiguration configured = source.getCorsConfiguration(request("http://localhost:12881"));
        assertThat(configured).isNotNull();
        assertThat(configured.getAllowedOrigins())
                .containsExactly("http://localhost:12881", "http://127.0.0.1:12881");
        assertThat(configured.getAllowedMethods()).contains("GET", "POST", "OPTIONS");
        assertThat(configured.getAllowedHeaders()).contains("Authorization", "Content-Type", "Idempotency-Key");
        assertThat(configured.checkOrigin("http://localhost:12881")).isEqualTo("http://localhost:12881");
        assertThat(configured.checkOrigin("https://unexpected.example")).isNull();
    }

    private static HttpServletRequest request(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/im/conversations/page");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "GET");
        request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
        return request;
    }
}
