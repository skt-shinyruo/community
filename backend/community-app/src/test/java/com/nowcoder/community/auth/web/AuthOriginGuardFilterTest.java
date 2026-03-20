package com.nowcoder.community.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.infra.security.origin.OriginGuardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AuthOriginGuardFilterTest {

    @Test
    void notAllowedOriginShouldBeForbiddenWithUtf8JsonBody() throws Exception {
        OriginGuardProperties props = new OriginGuardProperties();
        props.setAllowedOrigins(List.of("http://allowed.example"));

        AuthOriginGuardFilter filter = new AuthOriginGuardFilter(props, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/auth/login");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        request.addHeader("Origin", "http://evil.example");

        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, resp) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentType()).contains("charset=UTF-8");
        assertThat(response.getContentAsString()).contains("Origin 不被允许");
    }

    @Test
    void emptyAllowlistShouldBeForbiddenWithUtf8JsonBodyWhenFailClosed() throws Exception {
        OriginGuardProperties props = new OriginGuardProperties();
        props.setFailOpenWhenAllowlistEmpty(false);
        props.setAllowedOrigins(List.of());

        AuthOriginGuardFilter filter = new AuthOriginGuardFilter(props, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/auth/login");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        request.addHeader("Origin", "http://evil.example");

        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, resp) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentType()).contains("charset=UTF-8");
        assertThat(response.getContentAsString()).contains("Origin allowlist 未配置");
    }
}
