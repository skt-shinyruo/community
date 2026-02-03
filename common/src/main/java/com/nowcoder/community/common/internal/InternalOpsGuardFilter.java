package com.nowcoder.community.common.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.net.ClientIpResolver;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * internal 运维/高风险入口强保护（fail-closed）：
 * - 针对 /internal/** 中“高风险运维动作”（如 reindex/outbox replay）增加二次校验；
 * - 默认关闭（break-glass），需要显式开启开关 + ops-token + allowlist 才允许执行；
 * - 通过 Redis 提供并发(single-flight)与频率限制，避免误触/滥用。
 *
 * <p>注意：该过滤器不替代 internal-token；internal-token 仍由 InternalTokenFilter 兜底。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnBean(ClientIpResolver.class)
public class InternalOpsGuardFilter implements Filter {

    public static final String HEADER_OPS_TOKEN = "X-Ops-Token";

    private static final Logger log = LoggerFactory.getLogger(InternalOpsGuardFilter.class);

    private static final String PATH_PREFIX = "/internal/";

    private static final String OP_OUTBOX_REPLAY = "outbox-replay";
    private static final String OP_SEARCH_REINDEX = "search-reindex";
    private static final String OP_LIKE_BACKFILL = "like-backfill";

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final StringRedisTemplate redisTemplate;

    public InternalOpsGuardFilter(
            Environment environment,
            ObjectMapper objectMapper,
            ClientIpResolver clientIpResolver,
            StringRedisTemplate redisTemplate
    ) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI();
        if (!StringUtils.hasText(path) || !path.startsWith(PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        OpsRoute route = classify(path);
        if (route == null) {
            chain.doFilter(request, response);
            return;
        }

        String segment = extractFirstPathSegment(path);
        if (!StringUtils.hasText(segment) || !isValidSegment(segment)) {
            forbidden(resp, "internal 路径不合法");
            return;
        }

        if (!isEnabled(route.opName())) {
            forbidden(resp, "运维入口默认关闭（break-glass 未开启）");
            return;
        }

        ClientIpResolver.ResolvedClientIp ipInfo = clientIpResolver == null ? null : clientIpResolver.resolve(req);
        String clientIp = ipInfo == null ? null : ipInfo.ip();
        if (!isAllowedByAllowlist(route.opName(), clientIp)) {
            forbidden(resp, "来源未在 allowlist 中");
            return;
        }

        List<String> expectedTokens = resolveExpectedOpsTokens(segment);
        if (expectedTokens.isEmpty()) {
            forbidden(resp, "ops-token 未配置");
            return;
        }
        String token = req.getHeader(HEADER_OPS_TOKEN);
        if (!StringUtils.hasText(token) || !matchesAny(token, expectedTokens)) {
            forbidden(resp, "ops-token 无效");
            return;
        }

        if (redisTemplate == null) {
            serviceUnavailable(resp, "ops 限流存储不可用");
            return;
        }

        String scopeKey = segment.toLowerCase(Locale.ROOT) + ":" + route.opName();

        try {
            if (!acquireLock(scopeKey)) {
                conflict(resp, "运维操作正在执行中，请稍后重试");
                return;
            }
            try {
                if (!allowRate(scopeKey, clientIp)) {
                    tooMany(resp, "触发频率过高，请稍后重试");
                    return;
                }
                chain.doFilter(request, response);
            } finally {
                releaseLock(scopeKey);
            }
        } catch (Exception e) {
            log.warn("[internal-ops] blocked: op={} path={} err={}", route.opName(), path, e.toString());
            serviceUnavailable(resp, "ops 保护器不可用");
        }
    }

    private OpsRoute classify(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        if (path.endsWith("/outbox/replay")) {
            return new OpsRoute(OP_OUTBOX_REPLAY);
        }
        if ("/internal/search/reindex".equals(path)) {
            return new OpsRoute(OP_SEARCH_REINDEX);
        }
        if (path.endsWith("/likes/backfill")) {
            return new OpsRoute(OP_LIKE_BACKFILL);
        }
        return null;
    }

    private boolean isEnabled(String opName) {
        if (!StringUtils.hasText(opName)) {
            return false;
        }
        // 默认关闭：必须显式开启。
        String v = environment == null ? null : environment.getProperty("ops.guard." + opName + ".enabled");
        return "true".equalsIgnoreCase(StringUtils.hasText(v) ? v.trim() : "");
    }

    private boolean isAllowedByAllowlist(String opName, String clientIp) {
        if (!StringUtils.hasText(opName)) {
            return false;
        }
        String v = environment == null ? null : environment.getProperty("ops.guard." + opName + ".allowlist");
        String list = StringUtils.hasText(v) ? v.trim() : "";
        if (!StringUtils.hasText(list)) {
            // 默认 fail-closed：强制要求配置 allowlist，避免“只靠 token”导致误触范围过大
            return false;
        }
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }
        String ip = normalizeIp(clientIp);
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        for (String raw : list.split(",")) {
            String item = raw == null ? "" : raw.trim();
            if (!StringUtils.hasText(item)) {
                continue;
            }
            if (item.contains("/")) {
                if (cidrMatch(ip, item)) {
                    return true;
                }
                continue;
            }
            if (item.equals(ip)) {
                return true;
            }
        }
        return false;
    }

    private boolean acquireLock(String scopeKey) {
        int ttlSeconds = intProp("ops.guard.lock.ttl-seconds", 600);
        String key = "ops:lock:" + scopeKey;
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(Math.max(10, ttlSeconds))));
    }

    private void releaseLock(String scopeKey) {
        try {
            redisTemplate.delete("ops:lock:" + scopeKey);
        } catch (Exception ignored) {
        }
    }

    private boolean allowRate(String scopeKey, String clientIp) {
        int max = intProp("ops.guard.rate.max", 3);
        int windowSeconds = intProp("ops.guard.rate.window-seconds", 60);

        String ip = StringUtils.hasText(clientIp) ? normalizeIp(clientIp) : "unknown";
        String key = "ops:rate:" + scopeKey + ":" + ip;

        Long v = redisTemplate.opsForValue().increment(key);
        if (v == null) {
            return false;
        }
        if (v == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(Math.max(1, windowSeconds)));
        }
        return v <= max;
    }

    private int intProp(String key, int defaultValue) {
        if (environment == null || !StringUtils.hasText(key)) {
            return defaultValue;
        }
        String v = environment.getProperty(key);
        if (!StringUtils.hasText(v)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String extractFirstPathSegment(String path) {
        if (!StringUtils.hasText(path) || !path.startsWith(PATH_PREFIX)) {
            return "";
        }
        String rest = path.substring(PATH_PREFIX.length());
        if (!StringUtils.hasText(rest)) {
            return "";
        }
        int idx = rest.indexOf('/');
        String segment = idx < 0 ? rest : rest.substring(0, idx);
        return segment == null ? "" : segment.trim();
    }

    private boolean isValidSegment(String segment) {
        if (!StringUtils.hasText(segment)) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAny(String token, List<String> expectedTokens) {
        if (!StringUtils.hasText(token) || expectedTokens == null || expectedTokens.isEmpty()) {
            return false;
        }
        String t = token.trim();
        for (String expected : expectedTokens) {
            if (!StringUtils.hasText(expected)) {
                continue;
            }
            if (expected.equals(t)) {
                return true;
            }
        }
        return false;
    }

    private List<String> resolveExpectedOpsTokens(String segment) {
        // 分域：按 /internal/{segment} 提供 ops token（例如 ops.content.token）。
        List<String> tokens = new ArrayList<>(4);
        if (!StringUtils.hasText(segment) || environment == null) {
            return tokens;
        }
        String s = segment.trim();
        addIfPresent(tokens, "ops." + s + ".token");
        addIfPresent(tokens, "ops." + s + ".token-previous");
        // alias：users <-> user（历史/命名兼容，避免配置漂移导致大面积 403）
        if ("users".equalsIgnoreCase(s)) {
            addIfPresent(tokens, "ops.user.token");
            addIfPresent(tokens, "ops.user.token-previous");
        }
        return tokens;
    }

    private void addIfPresent(List<String> list, String key) {
        String v = getPropertyTrimmed(key);
        if (!StringUtils.hasText(v)) {
            return;
        }
        list.add(v);
    }

    private String getPropertyTrimmed(String key) {
        if (environment == null || !StringUtils.hasText(key)) {
            return "";
        }
        String v = environment.getProperty(key);
        if (!StringUtils.hasText(v)) {
            return "";
        }
        return v.trim();
    }

    private String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip.trim());
            return addr == null ? null : addr.getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

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
        } catch (Exception e) {
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
        } catch (Exception ignored) {
            return false;
        }
    }

    private void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(CommonErrorCode.FORBIDDEN.getCode(), message)));
    }

    private void conflict(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(409, message)));
    }

    private void tooMany(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(429, message)));
    }

    private void serviceUnavailable(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(CommonErrorCode.SERVICE_UNAVAILABLE.getCode(), message)));
    }

    private record OpsRoute(String opName) {
    }
}
