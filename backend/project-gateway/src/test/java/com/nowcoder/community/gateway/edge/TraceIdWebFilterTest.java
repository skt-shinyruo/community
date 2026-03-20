package com.nowcoder.community.gateway.edge;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdWebFilterTest {

    @Test
    void shouldGenerate32CharacterTraceIdWhenMissing() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts").build());
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        WebFilterChain chain = current -> {
            seenTraceId.set(current.getRequest().getHeaders().getFirst(TraceIdWebFilter.TRACE_ID_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(seenTraceId.get()).isNotBlank().hasSize(32).doesNotContain("-");
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdWebFilter.TRACE_ID_HEADER))
                .isEqualTo(seenTraceId.get());
    }
}
