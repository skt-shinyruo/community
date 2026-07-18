package com.nowcoder.community.common.web.net;

import com.nowcoder.community.common.net.TrustedProxyChain;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 服务侧客户端 IP 解析：
 * - 默认只使用 remoteAddr；
 * - 仅当 remoteAddr ∈ 可信代理 CIDR 时才解析 X-Forwarded-For。
 */
public class ClientIpResolver {

    public static final String SOURCE_REMOTE = "remote";
    public static final String SOURCE_XFF = "xff";

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final int MAX_FORWARDED_HOPS = 32;

    private final TrustedProxyChain trustedProxyChain;
    private final boolean readForwardedHeaders;

    public ClientIpResolver(TrustedProxyProperties properties) {
        List<String> trustedCidrs = List.of();
        if (properties != null && properties.isEnabled()) {
            List<String> configuredCidrs = properties.getCidrs();
            if (configuredCidrs != null && !configuredCidrs.isEmpty()) {
                trustedCidrs = configuredCidrs;
            }
        }
        this.trustedProxyChain = new TrustedProxyChain(trustedCidrs);
        this.readForwardedHeaders = !trustedCidrs.isEmpty();
    }

    public ResolvedClientIp resolve(HttpServletRequest request) {
        if (request == null) {
            return map(trustedProxyChain.resolve(null, List.of()));
        }
        List<String> forwardedHops = readForwardedHeaders ? forwardedHops(request) : List.of();
        return map(trustedProxyChain.resolve(request.getRemoteAddr(), forwardedHops));
    }

    private List<String> forwardedHops(HttpServletRequest request) {
        List<String> hops = new ArrayList<>(MAX_FORWARDED_HOPS + 1);
        Enumeration<String> headerLines = request.getHeaders(HEADER_X_FORWARDED_FOR);
        if (headerLines == null) {
            return hops;
        }
        while (headerLines.hasMoreElements()) {
            String headerLine = headerLines.nextElement();
            if (headerLine == null) {
                hops.add(null);
                if (hops.size() > MAX_FORWARDED_HOPS) {
                    return hops;
                }
                continue;
            }

            int tokenStart = 0;
            while (true) {
                int separator = headerLine.indexOf(',', tokenStart);
                int tokenEnd = separator < 0 ? headerLine.length() : separator;
                hops.add(headerLine.substring(tokenStart, tokenEnd));
                if (hops.size() > MAX_FORWARDED_HOPS) {
                    return hops;
                }
                if (separator < 0) {
                    break;
                }
                tokenStart = separator + 1;
            }
        }
        return hops;
    }

    private ResolvedClientIp map(TrustedProxyChain.Resolution resolution) {
        String source = resolution.source() == TrustedProxyChain.Source.DIRECT_PEER
                ? SOURCE_REMOTE
                : SOURCE_XFF;
        return new ResolvedClientIp(resolution.clientIp(), source);
    }

    public record ResolvedClientIp(String ip, String source) {
    }
}
