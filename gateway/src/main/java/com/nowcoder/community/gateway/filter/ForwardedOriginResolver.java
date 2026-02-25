package com.nowcoder.community.gateway.filter;

// 反向代理/HTTPS offload 兼容：在可信代理场景下解析 Forwarded/X-Forwarded-* 以恢复原始 scheme/host/port。
import com.nowcoder.community.platform.net.TrustedProxyProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.util.List;

@Component
public class ForwardedOriginResolver {

    private static final String HEADER_FORWARDED = "Forwarded";
    private static final String HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto";
    private static final String HEADER_X_FORWARDED_HOST = "X-Forwarded-Host";
    private static final String HEADER_X_FORWARDED_PORT = "X-Forwarded-Port";

    private final TrustedProxyProperties trustedProxyProperties;

    public ForwardedOriginResolver(TrustedProxyProperties trustedProxyProperties) {
        this.trustedProxyProperties = trustedProxyProperties;
    }

    public ResolvedOrigin resolve(ServerHttpRequest request) {
        if (request == null) {
            return null;
        }

        boolean trustForwarded = shouldTrustForwarded(request);

        String scheme = safeLower(request.getURI() == null ? null : request.getURI().getScheme());
        String host = request.getURI() == null ? null : request.getURI().getHost();
        Integer port = request.getURI() == null ? null : request.getURI().getPort();

        if (trustForwarded) {
            ForwardedValues forwarded = parseForwardedHeaders(request);
            if (forwarded != null) {
                if (StringUtils.hasText(forwarded.scheme())) {
                    scheme = safeLower(forwarded.scheme());
                }
                if (StringUtils.hasText(forwarded.host())) {
                    host = forwarded.host().trim();
                }
                if (forwarded.port() != null && forwarded.port() > 0) {
                    port = forwarded.port();
                }
            }
        }

        if (!StringUtils.hasText(host)) {
            ParsedHost parsed = parseHostAndPort(firstHeaderValue(request.getHeaders().getFirst("Host")));
            if (parsed != null && StringUtils.hasText(parsed.host())) {
                host = parsed.host();
                if (parsed.port() != null && parsed.port() > 0) {
                    port = parsed.port();
                }
            }
        }

        if (!StringUtils.hasText(scheme)) {
            scheme = "http";
        }

        int normalizedPort = normalizePort(scheme, port == null ? -1 : port);
        return new ResolvedOrigin(scheme, host, normalizedPort, trustForwarded ? "forwarded" : "request");
    }

    private boolean shouldTrustForwarded(ServerHttpRequest request) {
        if (trustedProxyProperties == null || !trustedProxyProperties.isEnabled()) {
            return false;
        }
        List<String> cidrs = trustedProxyProperties.getCidrs();
        if (cidrs == null || cidrs.isEmpty()) {
            return false;
        }
        String remoteIp = extractRemoteIp(request);
        if (!StringUtils.hasText(remoteIp)) {
            return false;
        }
        for (String cidr : cidrs) {
            if (!StringUtils.hasText(cidr)) {
                continue;
            }
            if (ClientIpResolver.cidrMatch(remoteIp, cidr.trim())) {
                return true;
            }
        }
        return false;
    }

    private String extractRemoteIp(ServerHttpRequest request) {
        if (request == null) {
            return null;
        }
        InetSocketAddress addr = request.getRemoteAddress();
        if (addr == null || addr.getAddress() == null) {
            return null;
        }
        return addr.getAddress().getHostAddress();
    }

    private ForwardedValues parseForwardedHeaders(ServerHttpRequest request) {
        if (request == null) {
            return null;
        }

        String proto = null;
        String host = null;
        Integer port = null;

        String forwarded = firstHeaderValue(request.getHeaders().getFirst(HEADER_FORWARDED));
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",", 2)[0].trim();
            for (String part : first.split(";")) {
                String item = part == null ? "" : part.trim();
                if (!StringUtils.hasText(item) || !item.contains("=")) {
                    continue;
                }
                String[] kv = item.split("=", 2);
                String k = kv[0] == null ? "" : kv[0].trim();
                String v = kv[1] == null ? "" : stripQuotes(kv[1].trim());
                if ("proto".equalsIgnoreCase(k) && StringUtils.hasText(v)) {
                    proto = v;
                }
                if ("host".equalsIgnoreCase(k) && StringUtils.hasText(v)) {
                    ParsedHost parsed = parseHostAndPort(v);
                    if (parsed != null && StringUtils.hasText(parsed.host())) {
                        host = parsed.host();
                        if (parsed.port() != null && parsed.port() > 0) {
                            port = parsed.port();
                        }
                    } else {
                        host = v;
                    }
                }
            }
        }

        if (!StringUtils.hasText(proto)) {
            proto = firstHeaderValue(request.getHeaders().getFirst(HEADER_X_FORWARDED_PROTO));
        }

        String xfh = firstHeaderValue(request.getHeaders().getFirst(HEADER_X_FORWARDED_HOST));
        if (!StringUtils.hasText(host) && StringUtils.hasText(xfh)) {
            ParsedHost parsed = parseHostAndPort(xfh);
            if (parsed != null && StringUtils.hasText(parsed.host())) {
                host = parsed.host();
                if (parsed.port() != null && parsed.port() > 0) {
                    port = parsed.port();
                }
            } else {
                host = xfh;
            }
        }

        String xfp = firstHeaderValue(request.getHeaders().getFirst(HEADER_X_FORWARDED_PORT));
        if (port == null && StringUtils.hasText(xfp)) {
            Integer p = parsePort(xfp);
            if (p != null && p > 0) {
                port = p;
            }
        }

        proto = safeLower(proto);
        if (!"http".equals(proto) && !"https".equals(proto)) {
            proto = null;
        }

        return new ForwardedValues(proto, host, port);
    }

    private String firstHeaderValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String first = raw.split(",", 2)[0].trim();
        return StringUtils.hasText(first) ? first : null;
    }

    private String safeLower(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String stripQuotes(String v) {
        if (!StringUtils.hasText(v)) {
            return v;
        }
        String s = v.trim();
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private ParsedHost parseHostAndPort(String hostHeader) {
        if (!StringUtils.hasText(hostHeader)) {
            return null;
        }
        String s = hostHeader.trim();
        if (s.startsWith("[")) {
            int idx = s.indexOf(']');
            if (idx < 0) {
                return null;
            }
            String host = s.substring(1, idx);
            Integer port = null;
            if (idx + 1 < s.length() && s.charAt(idx + 1) == ':') {
                port = parsePort(s.substring(idx + 2));
            }
            return new ParsedHost(host, port);
        }

        int lastColon = s.lastIndexOf(':');
        if (lastColon > 0 && lastColon < s.length() - 1) {
            String maybePort = s.substring(lastColon + 1);
            Integer p = parsePort(maybePort);
            if (p != null) {
                return new ParsedHost(s.substring(0, lastColon), p);
            }
        }
        return new ParsedHost(s, null);
    }

    private Integer parsePort(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            int p = Integer.parseInt(raw.trim());
            return p > 0 && p <= 65535 ? p : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int normalizePort(String scheme, int port) {
        if (port > 0) {
            return port;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return 80;
    }

    private record ForwardedValues(String scheme, String host, Integer port) {
    }

    private record ParsedHost(String host, Integer port) {
    }

    public record ResolvedOrigin(String scheme, String host, int port, String source) {
    }
}
