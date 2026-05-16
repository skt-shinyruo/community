package com.nowcoder.community.common.web;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceId;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void filterShouldExposeActiveOtelTraceIdAndTraceparent() throws Exception {
        SpanContext spanContext = SpanContext.create(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbb",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
            filter.doFilter(request, response, new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
                @Override
                protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse resp) {
                    assertThat(TraceId.get()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
                    assertThat(MDC.get("trace.id")).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
                    assertThat(MDC.get("span.id")).isEqualTo("bbbbbbbbbbbbbbbb");
                }
            }));
        }

        assertThat(response.getHeader(TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");
        assertThat(TraceId.get()).isNull();
        assertThat(MDC.get("trace.id")).isNull();
        assertThat(MDC.get("span.id")).isNull();
    }
}
