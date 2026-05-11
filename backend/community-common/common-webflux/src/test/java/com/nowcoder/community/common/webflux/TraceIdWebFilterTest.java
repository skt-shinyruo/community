package com.nowcoder.community.common.webflux;

import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdWebFilterTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    void shouldGenerateTraceparentWhenMissing() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts").build());
        AtomicReference<String> seenTraceparent = new AtomicReference<>();
        WebFilterChain chain = current -> {
            seenTraceparent.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        String traceId = TraceIdCodec.extractTraceIdFromTraceparent(seenTraceparent.get());
        assertThat(traceId).isNotBlank().hasSize(32);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo(seenTraceparent.get());
    }

    @Test
    void shouldPreserveValidIncomingTraceparent() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        String traceparent = traceparent(TRACE_ID);
        AtomicReference<String> seenTraceparent = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts")
                .header(TraceHeaders.HEADER_TRACEPARENT, traceparent)
                .build());
        WebFilterChain chain = current -> {
            seenTraceparent.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(seenTraceparent.get()).isEqualTo(traceparent);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo(traceparent);
    }

    @Test
    void shouldGenerateTraceparentWhenIncomingTraceparentIsInvalid() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        String invalidTraceparent = "ff-" + TRACE_ID + "-00f067aa0ba902b7-01";
        AtomicReference<String> seenTraceparent = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts")
                .header(TraceHeaders.HEADER_TRACEPARENT, invalidTraceparent)
                .build());
        WebFilterChain chain = current -> {
            seenTraceparent.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(seenTraceparent.get()).isNotEqualTo(invalidTraceparent);
        assertThat(TraceIdCodec.extractTraceIdFromTraceparent(seenTraceparent.get()))
                .isNotBlank()
                .hasSize(32);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo(seenTraceparent.get());
    }

    private static String traceparent(String traceId) {
        return "00-" + traceId + "-00f067aa0ba902b7-01";
    }
}
