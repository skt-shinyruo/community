package com.nowcoder.community.gateway.edge;

import com.nowcoder.community.common.net.TrustedProxyChain;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class ForwardedHeaderCanonicalizationWebFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;
    public static final String CANONICAL_CLIENT_IP_ATTRIBUTE =
            ForwardedHeaderCanonicalizationWebFilter.class.getName() + ".clientIp";

    static final String METRIC_NAME = "gateway.forwarded.header.canonicalization";

    private static final String FORWARDED = "Forwarded";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final int MAX_FORWARDED_HOPS = 32;
    private static final int MAX_COPIED_HOP_LENGTH = 65;
    private static final TrustedProxyChain LITERAL_NORMALIZER = new TrustedProxyChain(List.of());

    private final TrustedProxyChain trustedProxyChain;
    private final boolean readForwardedHeaders;
    private final MeterRegistry meterRegistry;

    public ForwardedHeaderCanonicalizationWebFilter(
            EdgeTrustedProxyProperties properties,
            MeterRegistry meterRegistry
    ) {
        List<String> trustedCidrs = List.of();
        if (properties != null && properties.isEnabled()) {
            List<String> configuredCidrs = properties.getCidrs();
            if (configuredCidrs != null && !configuredCidrs.isEmpty()) {
                trustedCidrs = configuredCidrs;
            }
        }
        this.trustedProxyChain = new TrustedProxyChain(trustedCidrs);
        this.readForwardedHeaders = !trustedCidrs.isEmpty();
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange == null || chain == null) {
            return Mono.empty();
        }

        ForwardedAdaptation adaptation = readForwardedHeaders
                ? forwardedHops(exchange.getRequest().getHeaders())
                : ForwardedAdaptation.empty();
        if (adaptation.truncated()) {
            record("truncated");
        } else if (adaptation.malformed()) {
            record("malformed");
        }

        TrustedProxyChain.Resolution resolution = trustedProxyChain.resolve(
                directPeer(exchange.getRequest().getRemoteAddress()),
                adaptation.hops()
        );
        String canonicalClientIp = resolution.clientIp();
        exchange.getAttributes().remove(CANONICAL_CLIENT_IP_ATTRIBUTE);
        if (StringUtils.hasText(canonicalClientIp)) {
            exchange.getAttributes().put(CANONICAL_CLIENT_IP_ATTRIBUTE, canonicalClientIp);
        }

        ServerWebExchange canonicalExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(FORWARDED);
                    headers.remove(X_FORWARDED_FOR);
                    headers.remove(X_REAL_IP);
                    if (StringUtils.hasText(canonicalClientIp)) {
                        headers.set(X_FORWARDED_FOR, canonicalClientIp);
                    }
                }))
                .build();
        return chain.filter(canonicalExchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private ForwardedAdaptation forwardedHops(HttpHeaders headers) {
        List<String> headerLines = headers.get(X_FORWARDED_FOR);
        if (headerLines == null || headerLines.isEmpty()) {
            return ForwardedAdaptation.empty();
        }

        List<String> hops = new ArrayList<>(MAX_FORWARDED_HOPS + 1);
        boolean malformed = false;
        for (String headerLine : headerLines) {
            if (headerLine == null) {
                hops.add(null);
                malformed = true;
                if (hops.size() > MAX_FORWARDED_HOPS) {
                    return new ForwardedAdaptation(hops, true, malformed);
                }
                continue;
            }

            int tokenStart = 0;
            while (true) {
                int separator = headerLine.indexOf(',', tokenStart);
                int tokenEnd = separator < 0 ? headerLine.length() : separator;
                String hop = boundedTokenCopy(headerLine, tokenStart, tokenEnd);
                hops.add(hop);
                malformed |= !isValidForwardedHop(hop);
                if (hops.size() > MAX_FORWARDED_HOPS) {
                    return new ForwardedAdaptation(hops, true, malformed);
                }
                if (separator < 0) {
                    break;
                }
                tokenStart = separator + 1;
            }
        }
        return new ForwardedAdaptation(hops, false, malformed);
    }

    private static String boundedTokenCopy(String headerLine, int tokenStart, int tokenEnd) {
        int copyEnd = Math.min(tokenEnd, tokenStart + MAX_COPIED_HOP_LENGTH);
        return headerLine.substring(tokenStart, copyEnd);
    }

    private static boolean isValidForwardedHop(String hop) {
        if (hop == null || hop.isEmpty() || hop.length() >= MAX_COPIED_HOP_LENGTH) {
            return false;
        }
        int start = 0;
        int end = hop.length();
        while (start < end && hop.charAt(start) == ' ') {
            start++;
        }
        while (end > start && hop.charAt(end - 1) == ' ') {
            end--;
        }
        if (start == end) {
            return false;
        }
        return LITERAL_NORMALIZER.resolve(hop.substring(start, end), List.of()).clientIp() != null;
    }

    private static String directPeer(InetSocketAddress remoteAddress) {
        if (remoteAddress == null) {
            return null;
        }
        InetAddress address = remoteAddress.getAddress();
        return address == null ? remoteAddress.getHostString() : address.getHostAddress();
    }

    private void record(String outcome) {
        if (meterRegistry != null) {
            meterRegistry.counter(METRIC_NAME, "outcome", outcome).increment();
        }
    }

    private record ForwardedAdaptation(List<String> hops, boolean truncated, boolean malformed) {

        private static ForwardedAdaptation empty() {
            return new ForwardedAdaptation(List.of(), false, false);
        }
    }
}
