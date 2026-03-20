package com.nowcoder.community.infra.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.infra.trace.TraceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityExceptionHandlerTest {

    @AfterEach
    void tearDown() {
        TraceId.clear();
    }

    @Test
    void commenceShouldWriteUtf8JsonBody() throws Exception {
        SecurityExceptionHandler handler = new SecurityExceptionHandler(new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentType()).contains("charset=UTF-8");
        assertThat(response.getContentAsString()).contains(CommonErrorCode.UNAUTHORIZED.getMessage());
    }

    @Test
    void accessDeniedShouldWriteUtf8JsonBody() throws Exception {
        SecurityExceptionHandler handler = new SecurityExceptionHandler(new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentType()).contains("charset=UTF-8");
        assertThat(response.getContentAsString()).contains(CommonErrorCode.FORBIDDEN.getMessage());
    }
}
