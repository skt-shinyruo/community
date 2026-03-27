package com.nowcoder.community.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.infra.security.origin.OriginGuardProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AuthOriginGuardFilterTest {

    @Test
    void notAllowedOriginShouldBeForbiddenWithUtf8JsonBody(CapturedOutput output) throws Exception {
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
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=origin_guard")
                .contains("community.outcome=denied")
                .contains("community.reason_code=origin_not_allowed")
                .contains("origin=http://evil.example")
                .contains("path=/api/auth/login");
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

    @Test
    void allowedLoopbackOriginShouldPass() throws Exception {
        OriginGuardProperties props = new OriginGuardProperties();
        props.setAllowedOrigins(List.of("http://127.0.0.1:12881"));

        AuthOriginGuardFilter filter = new AuthOriginGuardFilter(props, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/auth/login");
        request.setScheme("http");
        request.setServerName("community-app");
        request.setServerPort(8080);
        request.addHeader("Origin", "http://127.0.0.1:12881");

        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, resp) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void emptyAllowlistShouldLogDegradedWhenFailOpen(CapturedOutput output) throws Exception {
        OriginGuardProperties props = new OriginGuardProperties();
        props.setFailOpenWhenAllowlistEmpty(true);
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

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=origin_guard")
                .contains("community.outcome=degraded")
                .contains("community.reason_code=allowlist_empty_fail_open")
                .contains("origin=http://evil.example")
                .contains("path=/api/auth/login");
    }
}
