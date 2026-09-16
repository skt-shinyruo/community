package com.nowcoder.community.gateway.edge;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.ArrayList;

/**
 * Egress header hygiene: downstream services receive exactly one forwarding signal,
 * X-Forwarded-For = the gateway's remote address, already resolved from inbound
 * Forwarded/X-Forwarded-For by the Netty server customizer for trusted proxy peers
 * (spring.cloud.gateway.server.webflux.trusted-proxies). All other client-supplied
 * forwarding headers are dropped.
 *
 * ponytail: SCG 5.0.2's built-in XForwardedHeadersFilter cannot express this — in strict
 * (trusted-proxies) mode it strips the whole chain for untrusted resolved remotes and only
 * keeps trusted-proxy entries otherwise, so the real client IP never reaches downstream.
 */
public class CanonicalForwardedForHttpHeadersFilter implements HttpHeadersFilter, Ordered {

    // After the native XForwarded/Forwarded/Remove* header filters (order 0).
    public static final int ORDER = 1;

    private static final String FORWARDED = "Forwarded";
    private static final String X_FORWARDED_HEADER_PREFIX = "X-Forwarded-";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    @Override
    public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
        HttpHeaders output = new HttpHeaders();
        input.forEach((name, values) -> {
            if (!isForwardingHeader(name)) {
                output.put(name, new ArrayList<>(values));
            }
        });

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String host = remoteAddress == null ? null : remoteAddress.getHostString();
        if (StringUtils.hasText(host)) {
            output.set(X_FORWARDED_FOR, host);
        }
        return output;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static boolean isForwardingHeader(String name) {
        return FORWARDED.equalsIgnoreCase(name)
                || X_REAL_IP.equalsIgnoreCase(name)
                || name.regionMatches(true, 0, X_FORWARDED_HEADER_PREFIX, 0, X_FORWARDED_HEADER_PREFIX.length());
    }
}
