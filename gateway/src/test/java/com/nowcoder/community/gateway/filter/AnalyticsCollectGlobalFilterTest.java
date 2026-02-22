package com.nowcoder.community.gateway.filter;

import com.nowcoder.community.gateway.analytics.AnalyticsCollectDispatcher;
import com.nowcoder.community.gateway.config.AnalyticsCollectProperties;
import com.nowcoder.community.common.net.TrustedProxyProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AnalyticsCollectGlobalFilterTest {

    @Test
    void shouldDedupUvWithinSameGatewayInstance() {
        AnalyticsCollectProperties props = new AnalyticsCollectProperties();
        props.setEnabled(true);
        props.setTimeoutMs(500);
        props.setMaxConcurrency(10);
        props.setQueueCapacity(10_000);
        props.setDedupEnabled(true);
        props.setUvCacheMaxSize(10);
        props.setDauCacheMaxSize(10);
        props.setDedupTtlSeconds(60);

        ClientIpResolver ipResolver = new ClientIpResolver(new TrustedProxyProperties());
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AnalyticsCollectDispatcher dispatcher = mock(AnalyticsCollectDispatcher.class);

        AnalyticsCollectGlobalFilter filter = new AnalyticsCollectGlobalFilter(props, ipResolver, meterRegistry, dispatcher);
        GatewayFilterChain chain = exchange -> Mono.empty();

        MockServerHttpRequest request1 = MockServerHttpRequest.get("/api/posts")
                .remoteAddress(new InetSocketAddress("1.2.3.4", 12345))
                .build();
        filter.filter(MockServerWebExchange.from(request1), chain).block();

        MockServerHttpRequest request2 = MockServerHttpRequest.get("/api/posts")
                .remoteAddress(new InetSocketAddress("1.2.3.4", 12345))
                .build();
        filter.filter(MockServerWebExchange.from(request2), chain).block();

        verify(dispatcher, times(1)).trySubmitUv(anyString(), any(), any(), any());
    }

    @Test
    void shouldSkipDauWhenJwtSubjectNotNumeric() throws Exception {
        AnalyticsCollectProperties props = new AnalyticsCollectProperties();
        props.setEnabled(true);
        props.setTimeoutMs(500);
        props.setMaxConcurrency(10);
        props.setQueueCapacity(10_000);
        props.setDedupEnabled(true);
        props.setUvCacheMaxSize(10);
        props.setDauCacheMaxSize(10);
        props.setDedupTtlSeconds(60);

        ClientIpResolver ipResolver = new ClientIpResolver(new TrustedProxyProperties());
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AnalyticsCollectDispatcher dispatcher = mock(AnalyticsCollectDispatcher.class);

        AnalyticsCollectGlobalFilter filter = new AnalyticsCollectGlobalFilter(props, ipResolver, meterRegistry, dispatcher);
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
            public Mono<java.security.Principal> getPrincipal() {
                return Mono.just(token);
            }

            @Override
            public ServerHttpRequest getRequest() {
                return baseExchange.getRequest();
            }
        };

        filter.filter(exchange, chain).block();

        // principal subscribe 为异步：等待后台订阅跑完（避免测试偶发失败）
        Thread.sleep(50);

        verify(dispatcher, never()).trySubmitDau(anyInt(), any(), anyString(), anyString());
        assertThat(meterRegistry.find("gateway_analytics_collect_total")
                .tags("metric", "dau", "outcome", "skipped_bad_subject")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
