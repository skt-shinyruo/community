package com.nowcoder.community.platform.net;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.List;

/**
 * 服务侧客户端 IP 解析：
 * - 默认只使用 remoteAddr；
 * - 仅当 remoteAddr ∈ 可信代理 CIDR 时才解析 X-Forwarded-For（取第一个 IP）。
 */
public class ClientIpResolver {

    public static final String SOURCE_REMOTE = "remote";
    public static final String SOURCE_XFF = "xff";

    private final TrustedProxyProperties properties;

    public ClientIpResolver(TrustedProxyProperties properties) {
        this.properties = properties;
    }

    public ResolvedClientIp resolve(HttpServletRequest request) {
        if (request == null) {
            return new ResolvedClientIp(null, SOURCE_REMOTE);
        }
        String remoteIp = normalizeIp(request.getRemoteAddr());
        if (!shouldTrustForwarded(remoteIp)) {
            return new ResolvedClientIp(remoteIp, SOURCE_REMOTE);
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String candidate = firstIp(forwarded);
        String normalized = normalizeIp(candidate);
        if (StringUtils.hasText(normalized)) {
            return new ResolvedClientIp(normalized, SOURCE_XFF);
        }
        return new ResolvedClientIp(remoteIp, SOURCE_REMOTE);
    }

    private boolean shouldTrustForwarded(String remoteIp) {
        if (properties == null || !properties.isEnabled()) {
            return false;
        }
        List<String> cidrs = properties.getCidrs();
        if (cidrs == null || cidrs.isEmpty()) {
            return false;
        }
        if (!StringUtils.hasText(remoteIp)) {
            return false;
        }
        for (String cidr : cidrs) {
            if (!StringUtils.hasText(cidr)) {
                continue;
            }
            if (cidrMatch(remoteIp, cidr.trim())) {
                return true;
            }
        }
        return false;
    }

    private String firstIp(String forwarded) {
        if (!StringUtils.hasText(forwarded)) {
            return null;
        }
        String first = forwarded.split(",")[0].trim();
        return StringUtils.hasText(first) ? first : null;
    }

    private String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip.trim());
            return addr == null ? null : addr.getHostAddress();
        } catch (java.net.UnknownHostException e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 轻量 CIDR 匹配（支持 IPv4/IPv6）。
     */
    static boolean cidrMatch(String ip, String cidr) {
        if (!StringUtils.hasText(ip) || !StringUtils.hasText(cidr) || !cidr.contains("/")) {
            return false;
        }
        String[] parts = cidr.split("/", 2);
        String base = parts[0].trim();
        String prefixStr = parts[1].trim();
        if (!StringUtils.hasText(base) || !StringUtils.hasText(prefixStr)) {
            return false;
        }
        int prefix;
        try {
            prefix = Integer.parseInt(prefixStr);
        } catch (NumberFormatException e) {
            return false;
        }
        try {
            InetAddress cidrAddr = InetAddress.getByName(base);
            InetAddress ipAddr = InetAddress.getByName(ip.trim());
            byte[] cidrBytes = cidrAddr.getAddress();
            byte[] ipBytes = ipAddr.getAddress();
            if (cidrBytes == null || ipBytes == null || cidrBytes.length != ipBytes.length) {
                return false;
            }
            int totalBits = cidrBytes.length * 8;
            if (prefix < 0 || prefix > totalBits) {
                return false;
            }
            int fullBytes = prefix / 8;
            int remBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (cidrBytes[i] != ipBytes[i]) {
                    return false;
                }
            }
            if (remBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remBits)) & 0xFF;
            int a = (cidrBytes[fullBytes] & 0xFF) & mask;
            int b = (ipBytes[fullBytes] & 0xFF) & mask;
            return a == b;
        } catch (java.net.UnknownHostException ignored) {
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public record ResolvedClientIp(String ip, String source) {
    }
}
