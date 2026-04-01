package com.nowcoder.community.common.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        TraceId.clear();
    }

    @Test
    void commence_shouldWriteResultJsonAndTraceHeaders() throws Exception {
        TraceId.set("ABCDEFABCDEFABCDEFABCDEFABCDEFAB");
        SecurityExceptionHandler handler = new SecurityExceptionHandler(objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, null);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader(TraceHeaders.HEADER_TRACE_ID)).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
        assertThat(body.path("code").asInt()).isEqualTo(CommonErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    void handle_shouldWriteForbiddenResult() throws Exception {
        TraceId.set("ABCDEFABCDEFABCDEFABCDEFABCDEFAB");
        SecurityExceptionHandler handler = new SecurityExceptionHandler(objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader(TraceHeaders.HEADER_TRACE_ID)).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
        assertThat(body.path("code").asInt()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
    }
}
