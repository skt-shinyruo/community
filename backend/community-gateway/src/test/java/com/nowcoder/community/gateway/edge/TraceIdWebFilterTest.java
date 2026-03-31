package com.nowcoder.community.gateway.edge;

import com.nowcoder.community.common.trace.TraceHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdWebFilterTest {

    private static final String TRACEPARENT_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String LEGACY_TRACE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String UPPERCASE_LEGACY_TRACE_ID = "ABCDEFABCDEFABCDEFABCDEFABCDEFAB";

    @Test
    void shouldGenerate32CharacterTraceIdWhenMissing() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts").build());
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        WebFilterChain chain = current -> {
            seenTraceId.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(seenTraceId.get()).isNotBlank().hasSize(32).doesNotContain("-");
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID))
                .isEqualTo(seenTraceId.get());
    }

    @Test
    void shouldPreferTraceparentTraceIdOverLegacyHeader() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        AtomicReference<String> seenTraceparent = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts")
                .header(TraceHeaders.HEADER_TRACE_ID, LEGACY_TRACE_ID)
                .header(TraceHeaders.HEADER_TRACEPARENT, traceparent(TRACEPARENT_TRACE_ID))
                .build());
        WebFilterChain chain = current -> {
            seenTraceId.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID));
            seenTraceparent.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(seenTraceId.get()).isEqualTo(TRACEPARENT_TRACE_ID);
        assertThat(seenTraceparent.get()).contains(TRACEPARENT_TRACE_ID);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID))
                .isEqualTo(TRACEPARENT_TRACE_ID);
    }

    @Test
    void shouldFallbackToNormalizedLegacyTraceIdWhenTraceparentMissing() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        AtomicReference<String> seenTraceparent = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts")
                .header(TraceHeaders.HEADER_TRACE_ID, UPPERCASE_LEGACY_TRACE_ID)
                .build());
        WebFilterChain chain = current -> {
            seenTraceId.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID));
            seenTraceparent.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(seenTraceId.get()).isEqualTo(UPPERCASE_LEGACY_TRACE_ID.toLowerCase());
        assertThat(seenTraceparent.get()).startsWith("00-" + UPPERCASE_LEGACY_TRACE_ID.toLowerCase() + "-");
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID))
                .isEqualTo(UPPERCASE_LEGACY_TRACE_ID.toLowerCase());
    }

    @Test
    void shouldFallbackToLegacyTraceIdWhenTraceparentVersionInvalid() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        AtomicReference<String> seenTraceparent = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts")
                .header(TraceHeaders.HEADER_TRACE_ID, UPPERCASE_LEGACY_TRACE_ID)
                .header(TraceHeaders.HEADER_TRACEPARENT, invalidVersionTraceparent(TRACEPARENT_TRACE_ID))
                .build());
        WebFilterChain chain = current -> {
            seenTraceId.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID));
            seenTraceparent.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(seenTraceId.get()).isEqualTo(UPPERCASE_LEGACY_TRACE_ID.toLowerCase());
        assertThat(seenTraceparent.get()).startsWith("00-" + UPPERCASE_LEGACY_TRACE_ID.toLowerCase() + "-");
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID))
                .isEqualTo(UPPERCASE_LEGACY_TRACE_ID.toLowerCase());
    }

    @Test
    void shouldGenerateTraceIdWhenTraceparentSpanIdAllZerosAndLegacyMissing() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        AtomicReference<String> seenTraceparent = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts")
                .header(TraceHeaders.HEADER_TRACEPARENT, zeroSpanTraceparent(TRACEPARENT_TRACE_ID))
                .build());
        WebFilterChain chain = current -> {
            seenTraceId.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID));
            seenTraceparent.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(seenTraceId.get()).isNotBlank().hasSize(32).matches("[0-9a-f]{32}");
        assertThat(seenTraceparent.get()).startsWith("00-" + seenTraceId.get() + "-");
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID))
                .isEqualTo(seenTraceId.get());
    }

    @Test
    void shouldReplaceInvalidLegacyTraceIdWithGeneratedTraceId() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        String invalidLegacyTraceId = "trace-123";
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        AtomicReference<String> seenTraceparent = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts")
                .header(TraceHeaders.HEADER_TRACE_ID, invalidLegacyTraceId)
                .build());
        WebFilterChain chain = current -> {
            seenTraceId.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID));
            seenTraceparent.set(current.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(seenTraceId.get()).isNotBlank().hasSize(32).matches("[0-9a-f]{32}");
        assertThat(seenTraceId.get()).isNotEqualTo(invalidLegacyTraceId);
        assertThat(seenTraceparent.get()).startsWith("00-" + seenTraceId.get() + "-");
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID))
                .isEqualTo(seenTraceId.get());
    }

    private static String traceparent(String traceId) {
        return "00-" + traceId + "-00f067aa0ba902b7-01";
    }

    private static String invalidVersionTraceparent(String traceId) {
        return "ff-" + traceId + "-00f067aa0ba902b7-01";
    }

    private static String zeroSpanTraceparent(String traceId) {
        return "00-" + traceId + "-0000000000000000-01";
    }
}
