package com.nowcoder.community.gateway.edge;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.util.ArrayList;

public class CanonicalForwardedForHttpHeadersFilter implements HttpHeadersFilter, Ordered {

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

        String canonicalClientIp = exchange.getAttribute(
                ForwardedHeaderCanonicalizationWebFilter.CANONICAL_CLIENT_IP_ATTRIBUTE
        );
        if (StringUtils.hasText(canonicalClientIp)) {
            output.set(X_FORWARDED_FOR, canonicalClientIp);
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
