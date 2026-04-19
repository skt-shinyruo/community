package com.nowcoder.community.gateway.edge;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitWebFilterTest {

    @Test
    void shouldBlockAfterLimitForPrincipalKey() {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setLimit(1);
        policy.setWindow(Duration.ofMinutes(1));
        properties.getPolicies().put("/limited", policy);

        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.allow("principal:alice:/limited", policy)).thenReturn(true, false);
        RateLimitWebFilter filter = new RateLimitWebFilter(properties, limiter);

        AtomicInteger chainInvocations = new AtomicInteger();
        WebFilterChain chain = exchange -> {
            chainInvocations.incrementAndGet();
            return Mono.empty();
        };

        Principal principal = () -> "alice";
        InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 8080);

        ServerWebExchange first = buildExchange("/limited", principal, remote);
        filter.filter(first, chain).block();
        assertThat(chainInvocations).hasValue(1);

        ServerWebExchange second = buildExchange("/limited", principal, remote);
        filter.filter(second, chain).block();
        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(chainInvocations).hasValue(1);
    }

    @Test
    void shouldBlockByIpWhenPrincipalIsMissing() {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setLimit(1);
        policy.setWindow(Duration.ofSeconds(30));
        properties.getPolicies().put("/ip-limited", policy);

        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.allow("ip:10.10.10.10:/ip-limited", policy)).thenReturn(true, false);
        RateLimitWebFilter filter = new RateLimitWebFilter(properties, limiter);

        AtomicInteger chainInvocations = new AtomicInteger();
        WebFilterChain chain = exchange -> {
            chainInvocations.incrementAndGet();
            return Mono.empty();
        };

        InetSocketAddress remote = new InetSocketAddress("10.10.10.10", 9090);
        ServerWebExchange first = buildExchange("/ip-limited", null, remote);
        filter.filter(first, chain).block();
        assertThat(chainInvocations).hasValue(1);

        ServerWebExchange second = buildExchange("/ip-limited", null, remote);
        filter.filter(second, chain).block();
        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(chainInvocations).hasValue(1);
    }

    @Test
    void shouldSkipWhenPolicyDisabled() {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setLimit(1);
        policy.setWindow(Duration.ofSeconds(30));
        policy.setEnabled(false);
        properties.getPolicies().put("/disabled", policy);

        RateLimiter limiter = mock(RateLimiter.class);
        RateLimitWebFilter filter = new RateLimitWebFilter(properties, limiter);

        AtomicInteger chainInvocations = new AtomicInteger();
        WebFilterChain chain = exchange -> {
            chainInvocations.incrementAndGet();
            return Mono.empty();
        };

        ServerWebExchange exchange = buildExchange("/disabled", null, new InetSocketAddress("127.0.0.1", 9999));
        filter.filter(exchange, chain).block();

        assertThat(chainInvocations).hasValue(1);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void shouldFallBackToIpWhenPrincipalNameIsBlank() {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setLimit(1);
        policy.setWindow(Duration.ofSeconds(30));
        properties.getPolicies().put("/blank-principal", policy);

        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.allow("ip:10.10.10.11:/blank-principal", policy)).thenReturn(true, false);
        RateLimitWebFilter filter = new RateLimitWebFilter(properties, limiter);

        AtomicInteger chainInvocations = new AtomicInteger();
        WebFilterChain chain = exchange -> {
            chainInvocations.incrementAndGet();
            return Mono.empty();
        };

        Principal blankPrincipal = () -> "";
        InetSocketAddress remote = new InetSocketAddress("10.10.10.11", 9090);

        ServerWebExchange first = buildExchange("/blank-principal", blankPrincipal, remote);
        filter.filter(first, chain).block();
        assertThat(chainInvocations).hasValue(1);

        ServerWebExchange second = buildExchange("/blank-principal", blankPrincipal, remote);
        filter.filter(second, chain).block();
        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(chainInvocations).hasValue(1);
    }

    @Test
    void shouldBlockWhenLimiterErrorsByDefault() {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        properties.getPolicies().put("/limited", policy);

        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.allow("ip:127.0.0.1:/limited", policy)).thenThrow(new RuntimeException("redis down"));
        RateLimitWebFilter filter = new RateLimitWebFilter(properties, limiter);

        AtomicInteger chainInvocations = new AtomicInteger();
        WebFilterChain chain = exchange -> {
            chainInvocations.incrementAndGet();
            return Mono.empty();
        };

        ServerWebExchange exchange = buildExchange("/limited", null, new InetSocketAddress("127.0.0.1", 8080));
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(chainInvocations).hasValue(0);
    }

    @Test
    void shouldAllowWhenLimiterErrorsAndFailOpenEnabled() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setFailOpenOnError(true);
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        properties.getPolicies().put("/limited", policy);

        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.allow("ip:127.0.0.1:/limited", policy)).thenThrow(new RuntimeException("redis down"));
        RateLimitWebFilter filter = new RateLimitWebFilter(properties, limiter);

        AtomicInteger chainInvocations = new AtomicInteger();
        WebFilterChain chain = exchange -> {
            chainInvocations.incrementAndGet();
            return Mono.empty();
        };

        ServerWebExchange exchange = buildExchange("/limited", null, new InetSocketAddress("127.0.0.1", 8080));
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(chainInvocations).hasValue(1);
    }

    private ServerWebExchange buildExchange(String path, Principal principal, InetSocketAddress remoteAddress) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).remoteAddress(remoteAddress).build()
        );
        if (principal == null) {
            return exchange;
        }
        ServerWebExchange decorated = new ServerWebExchangeDecorator(exchange) {
            @Override
            public <T extends Principal> Mono<T> getPrincipal() {
                @SuppressWarnings("unchecked")
                T typed = (T) principal;
                return Mono.just(typed);
            }
        };
        return decorated;
    }
}
