package com.nowcoder.community.gateway.filter;

import com.nowcoder.community.gateway.config.AnalyticsCollectProperties;
import com.nowcoder.community.gateway.config.TrustedProxyProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsCollectGlobalFilterTest {

    @Test
    void shouldDedupUvWithinSameGatewayInstance() {
        AtomicInteger calls = new AtomicInteger();
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                });

        AnalyticsCollectProperties props = new AnalyticsCollectProperties();
        props.setEnabled(true);
        props.setInternalToken("t");
        props.setTimeoutMs(500);
        props.setMaxConcurrency(10);
        props.setDedupEnabled(true);
        props.setUvCacheMaxSize(10);
        props.setDauCacheMaxSize(10);
        props.setDedupTtlSeconds(60);

        ClientIpResolver ipResolver = new ClientIpResolver(new TrustedProxyProperties());
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        AnalyticsCollectGlobalFilter filter = new AnalyticsCollectGlobalFilter(props, webClientBuilder, ipResolver, meterRegistry);
        GatewayFilterChain chain = exchange -> Mono.empty();

        MockServerHttpRequest request1 = MockServerHttpRequest.get("/api/posts")
                .remoteAddress(new InetSocketAddress("1.2.3.4", 12345))
                .build();
        filter.filter(MockServerWebExchange.from(request1), chain).block();

        MockServerHttpRequest request2 = MockServerHttpRequest.get("/api/posts")
                .remoteAddress(new InetSocketAddress("1.2.3.4", 12345))
                .build();
        filter.filter(MockServerWebExchange.from(request2), chain).block();

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void shouldSkipWhenMaxConcurrencyExceeded() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(request -> Mono.delay(Duration.ofMillis(200))
                        .map(ignored -> {
                            calls.incrementAndGet();
                            return ClientResponse.create(HttpStatus.OK).build();
                        }));

        AnalyticsCollectProperties props = new AnalyticsCollectProperties();
        props.setEnabled(true);
        props.setInternalToken("t");
        props.setTimeoutMs(2000);
        props.setMaxConcurrency(1);
        props.setDedupEnabled(true);
        props.setUvCacheMaxSize(100);
        props.setDauCacheMaxSize(100);
        props.setDedupTtlSeconds(60);

        ClientIpResolver ipResolver = new ClientIpResolver(new TrustedProxyProperties());
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        AnalyticsCollectGlobalFilter filter = new AnalyticsCollectGlobalFilter(props, webClientBuilder, ipResolver, meterRegistry);
        GatewayFilterChain chain = exchange -> Mono.empty();

        MockServerHttpRequest request1 = MockServerHttpRequest.get("/api/posts")
                .remoteAddress(new InetSocketAddress("1.2.3.4", 12345))
                .build();
        filter.filter(MockServerWebExchange.from(request1), chain).block();

        MockServerHttpRequest request2 = MockServerHttpRequest.get("/api/posts")
                .remoteAddress(new InetSocketAddress("5.6.7.8", 23456))
                .build();
        filter.filter(MockServerWebExchange.from(request2), chain).block();

        // 等待第一次调用完成，避免后台订阅泄露到后续测试。
        Thread.sleep(250);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(meterRegistry.find("gateway_analytics_collect_total")
                .tags("metric", "uv", "outcome", "skipped_concurrency")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void shouldSkipDauWhenJwtSubjectNotNumeric() {
        AtomicInteger calls = new AtomicInteger();
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                });

        AnalyticsCollectProperties props = new AnalyticsCollectProperties();
        props.setEnabled(true);
        props.setInternalToken("t");
        props.setTimeoutMs(500);
        props.setMaxConcurrency(10);
        props.setDedupEnabled(true);
        props.setUvCacheMaxSize(10);
        props.setDauCacheMaxSize(10);
        props.setDedupTtlSeconds(60);

        ClientIpResolver ipResolver = new ClientIpResolver(new TrustedProxyProperties());
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        AnalyticsCollectGlobalFilter filter = new AnalyticsCollectGlobalFilter(props, webClientBuilder, ipResolver, meterRegistry);
        GatewayFilterChain chain = exchange -> Mono.empty();

        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("not-a-number")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/posts")
                .remoteAddress(new InetSocketAddress("1.2.3.4", 12345))
                .build();
        MockServerWebExchange baseExchange = MockServerWebExchange.from(request);
        ServerWebExchange exchange = new ServerWebExchangeDecorator(baseExchange) {
            @Override
            public Mono<Principal> getPrincipal() {
                return Mono.just(token);
            }
        };

        filter.filter(exchange, chain).block();

        // UV 会被采集一次，DAU 因 subject 非数字被跳过。
        assertThat(calls.get()).isEqualTo(1);
        assertThat(meterRegistry.find("gateway_analytics_collect_total")
                .tags("metric", "dau", "outcome", "skipped_bad_subject")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
