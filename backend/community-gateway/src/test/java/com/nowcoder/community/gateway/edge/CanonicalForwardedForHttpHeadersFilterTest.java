package com.nowcoder.community.gateway.edge;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.RemoveForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.RemoveXForwardedHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalForwardedForHttpHeadersFilterTest {

    private static final String FORWARDED = "Forwarded";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    private final CanonicalForwardedForHttpHeadersFilter filter =
            new CanonicalForwardedForHttpHeadersFilter();

    @Test
    void shouldRestoreOnlyCanonicalForwardedForAfterGatewayRemovalFilters() {
        ServerWebExchange exchange = exchange();
        exchange.getAttributes().put(
                ForwardedHeaderCanonicalizationWebFilter.CANONICAL_CLIENT_IP_ATTRIBUTE,
                "198.51.100.77"
        );
        HttpHeaders input = new HttpHeaders();
        input.add(FORWARDED, "for=203.0.113.7;proto=https");
        input.add(X_FORWARDED_FOR, "203.0.113.7");
        input.add(X_FORWARDED_FOR, "198.51.100.77");
        input.add("X-Forwarded-Host", "attacker.example");
        input.add("X-Forwarded-Client-Cert", "spoofed-certificate");
        input.add(X_REAL_IP, "203.0.113.8");
        input.add("X-Request-Id", "request-1");

        HttpHeaders output = HttpHeadersFilter.filter(
                List.of(
                        new RemoveForwardedHeadersFilter(),
                        new RemoveXForwardedHeadersFilter(),
                        filter
                ),
                input,
                exchange,
                HttpHeadersFilter.Type.REQUEST
        );

        assertThat(output.get(X_FORWARDED_FOR)).containsExactly("198.51.100.77");
        assertThat(output.get(FORWARDED)).isNull();
        assertThat(output.get("X-Forwarded-Host")).isNull();
        assertThat(output.get("X-Forwarded-Client-Cert")).isNull();
        assertThat(output.get(X_REAL_IP)).isNull();
        assertThat(output.get("X-Request-Id")).containsExactly("request-1");
        assertThat(input.get(X_FORWARDED_FOR)).containsExactly("203.0.113.7", "198.51.100.77");
    }

    @Test
    void shouldRemoveForwardingHeadersWhenCanonicalClientIsUnavailable() {
        HttpHeaders input = new HttpHeaders();
        input.add(FORWARDED, "for=203.0.113.7");
        input.add(X_FORWARDED_FOR, "203.0.113.7");
        input.add("x-forwarded-proto", "https");
        input.add("x-real-ip", "203.0.113.8");

        HttpHeaders output = filter.filter(input, exchange());

        assertThat(output.get(FORWARDED)).isNull();
        assertThat(output.get(X_FORWARDED_FOR)).isNull();
        assertThat(output.get("X-Forwarded-Proto")).isNull();
        assertThat(output.get(X_REAL_IP)).isNull();
    }

    @Test
    void shouldRunAfterGatewayRemovalFilters() {
        assertThat(new RemoveForwardedHeadersFilter().getOrder()).isZero();
        assertThat(new RemoveXForwardedHeadersFilter().getOrder()).isZero();
        assertThat(filter.getOrder()).isGreaterThan(0);
    }

    private static ServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
    }
}
