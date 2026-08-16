package com.nowcoder.community.gateway.edge;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AccessLogWebFilterTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        MDC.clear();
    }

    @Test
    void accessLogShouldUseResolvedTraceIdAndRestorePreviousMdcAfterExchange(CapturedOutput output) {
        String staleTraceId = "stale-mdc-trace";
        String resolvedTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        MDC.put(TraceContext.MDC_KEY_TRACE_ID, staleTraceId);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts")
                .header(TraceHeaders.HEADER_TRACEPARENT, traceparent(resolvedTraceId))
                .build());

        com.nowcoder.community.common.webflux.TraceIdWebFilter traceIdWebFilter =
                new com.nowcoder.community.common.webflux.TraceIdWebFilter();
        AccessLogWebFilter accessLogWebFilter = new AccessLogWebFilter();

        traceIdWebFilter.filter(exchange, current -> accessLogWebFilter.filter(current, tracedExchange -> {
            assertThat(MDC.get(TraceContext.MDC_KEY_TRACE_ID)).isEqualTo(staleTraceId);
            tracedExchange.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        })).block();

        assertThat(output.getAll())
                .contains(AccessLogWebFilter.class.getSimpleName())
                .contains("method=GET")
                .contains("path=/api/posts")
                .contains("status=200")
                .contains("traceId=" + resolvedTraceId);
        assertThat(MDC.get(TraceContext.MDC_KEY_TRACE_ID)).isEqualTo(staleTraceId);
    }

    @Test
    void gatewayFiltersShouldDeclareExplicitOrdering() {
        com.nowcoder.community.common.webflux.TraceIdWebFilter traceIdWebFilter =
                new com.nowcoder.community.common.webflux.TraceIdWebFilter();
        AccessLogWebFilter accessLogWebFilter = new AccessLogWebFilter();

        assertThat(traceIdWebFilter).isInstanceOf(Ordered.class);
        assertThat(accessLogWebFilter).isInstanceOf(Ordered.class);

        Ordered traceOrdered = (Ordered) traceIdWebFilter;
        Ordered accessOrdered = (Ordered) accessLogWebFilter;
        assertThat(traceOrdered.getOrder()).isLessThan(accessOrdered.getOrder());
    }

    private static String traceparent(String traceId) {
        return "00-" + traceId + "-00f067aa0ba902b7-01";
    }
}
