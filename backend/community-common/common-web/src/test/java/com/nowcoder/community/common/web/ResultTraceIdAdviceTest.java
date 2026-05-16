package com.nowcoder.community.common.web;

import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTraceIdAdviceTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void beforeBodyWrite_shouldWrapPayloadAndFillTraceId() throws Exception {
        ResultTraceIdAdvice advice = new ResultTraceIdAdvice();
        SamplePayload payload = new SamplePayload("hello");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
        SpanContext spanContext = SpanContext.create(
                "abcdefabcdefabcdefabcdefabcdefab",
                "1234567890abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );

        try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
            Object body = advice.beforeBodyWrite(
                    payload,
                    returnType("plainPayload"),
                    null,
                    null,
                    new ServletServerHttpRequest(servletRequest),
                    response
            );

            assertThat(body).isInstanceOf(Result.class);
            Result<?> result = (Result<?>) body;
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).isSameAs(payload);
            assertThat(result.getTraceId()).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
            assertThat(response.getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT))
                    .isEqualTo("00-abcdefabcdefabcdefabcdefabcdefab-1234567890abcdef-01");
        }
    }

    private static MethodParameter returnType(String methodName) throws NoSuchMethodException {
        return new MethodParameter(SampleReturnTypes.class.getMethod(methodName), -1);
    }

    static class SampleReturnTypes {

        public SamplePayload plainPayload() {
            return null;
        }
    }

    static class SamplePayload {

        private final String value;

        SamplePayload(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
