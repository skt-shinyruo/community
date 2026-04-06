package com.nowcoder.community.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;

class GatewayCorsConfigTest {

    @Test
    void shouldNotCrashWhenWebSocketHandshakeCarriesOriginButMockRequestHasNoHost() {
        GatewayCorsProperties properties = new GatewayCorsProperties();
        properties.setAllowedOrigins(java.util.List.of("http://localhost:12881"));

        var filter = new GatewayCorsConfig().gatewayCorsWebFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/im")
                        .header("Origin", "http://localhost:12881")
                        .header("Connection", "Upgrade")
                        .header("Upgrade", "websocket")
                        .build()
        );

        assertThatCode(() -> filter.filter(exchange, ignored -> Mono.empty()).block(Duration.ofSeconds(5)))
                .doesNotThrowAnyException();
    }
}
