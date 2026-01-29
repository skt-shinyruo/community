package com.nowcoder.community.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.auth.config.OriginGuardProperties;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * auth-service 服务侧 OriginGuard：
 * - 防止绕过 gateway 直接访问 auth-service 时降低安全性；
 * - 仅覆盖 cookie 会话相关敏感入口（login/refresh/logout）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@ConditionalOnBean(OriginGuardProperties.class)
public class AuthOriginGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthOriginGuardFilter.class);

    private final OriginGuardProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean warnedEmptyAllowlist = new AtomicBoolean(false);

    public AuthOriginGuardFilter(OriginGuardProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (request == null) {
            return true;
        }
        if (properties == null || !properties.isEnabled()) {
            return true;
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !("/api/auth/login".equals(path) || "/api/auth/refresh".equals(path) || "/api/auth/logout".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (!StringUtils.hasText(origin)) {
            // 与 gateway 行为一致：无 Origin 头直接放行（兼容非浏览器客户端）
            filterChain.doFilter(request, response);
            return;
        }

        if (isSameOrigin(request, origin)) {
            filterChain.doFilter(request, response);
            return;
        }

        List<String> allowed = properties.getAllowedOrigins();
        if (allowed == null || allowed.isEmpty()) {
            if (properties.isFailOpenWhenAllowlistEmpty()) {
                if (warnedEmptyAllowlist.compareAndSet(false, true)) {
                    log.warn("[origin-guard] allowed-origins 为空，已退化为 fail-open（建议在配置中心补齐 allowlist）");
                }
                filterChain.doFilter(request, response);
                return;
            }
            forbidden(response, "Origin allowlist 未配置");
            return;
        }

        if (allowed.contains(origin)) {
            filterChain.doFilter(request, response);
            return;
        }

        forbidden(response, "Origin 不被允许");
    }

    private boolean isSameOrigin(HttpServletRequest request, String origin) {
        if (request == null || !StringUtils.hasText(origin)) {
            return false;
        }
        URI originUri;
        try {
            originUri = URI.create(origin.trim());
        } catch (Exception ignored) {
            return false;
        }

        String oScheme = originUri.getScheme();
        String oHost = originUri.getHost();
        int oPort = normalizePort(oScheme, originUri.getPort());
        if (!StringUtils.hasText(oScheme) || !StringUtils.hasText(oHost)) {
            return false;
        }

        String rScheme = request.getScheme();
        String rHost = request.getServerName();
        int rPort = normalizePort(rScheme, request.getServerPort());
        return oScheme.equalsIgnoreCase(rScheme) && oHost.equalsIgnoreCase(rHost) && oPort == rPort;
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

    private void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<?> body = Result.error(CommonErrorCode.FORBIDDEN.getCode(), message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
