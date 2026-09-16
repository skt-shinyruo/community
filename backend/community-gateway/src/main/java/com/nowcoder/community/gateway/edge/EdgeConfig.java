package com.nowcoder.community.gateway.edge;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;

@Configuration(proxyBeanMethods = false)
public class EdgeConfig {

    @Bean
    AccessLogWebFilter accessLogWebFilter() {
        return new AccessLogWebFilter();
    }

    @Bean
    CanonicalForwardedForHttpHeadersFilter canonicalForwardedForHttpHeadersFilter() {
        return new CanonicalForwardedForHttpHeadersFilter();
    }

    /**
     * Rate-limit bucket key: authenticated principal name, else client IP.
     * When spring.cloud.gateway.server.webflux.trusted-proxies is set, the gateway's Netty
     * server customizer resolves X-Forwarded-For into the exchange remote address for trusted
     * peers, so getRemoteAddress() is already the real client IP (untrusted peers keep their
     * true peer address, preserving anti-spoofing for bucket keying).
     */
    @Bean
    KeyResolver gatewayRateLimitKeyResolver() {
        return exchange -> exchange.<Principal>getPrincipal()
                .map(Principal::getName)
                .filter(StringUtils::hasText)
                .map(name -> "principal:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + remoteHost(exchange)));
    }

    private static String remoteHost(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String host = remoteAddress == null ? null : remoteAddress.getHostString();
        return StringUtils.hasText(host) ? host : "unknown";
    }
}
