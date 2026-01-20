package com.nowcoder.community.common.internal;

/**
 * internal 接口最小权限过滤器：对 /internal/** 强制校验 X-Internal-Token。
 *
 * <p>说明：各服务的 Spring Security 通常会对 /internal/** 放行（不走 JWT），由该过滤器兜底保护，
 * 避免后续新增 internal controller 时遗漏手写校验。</p>
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InternalTokenFilter implements Filter {

    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";
    private static final String PATH_PREFIX = "/internal/";

    private final Environment environment;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI();
        if (path == null || !path.startsWith(PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String segment = extractFirstPathSegment(path);
        String expected = resolveExpectedToken(segment);
        if (!StringUtils.hasText(expected)) {
            forbidden(resp, "internal-token 未配置");
            return;
        }

        String token = req.getHeader(HEADER_INTERNAL_TOKEN);
        if (!StringUtils.hasText(token) || !expected.equals(token)) {
            forbidden(resp, "internal-token 无效");
            return;
        }

        chain.doFilter(request, response);
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

    private String resolveExpectedToken(String segment) {
        // 1) service 专用 token：<segment>.internal-token（例如 content.internal-token）
        // 2) alias：users -> user.internal-token（历史/命名兼容）
        // 3) 全局 token：internal.token（等价 env: INTERNAL_TOKEN）

        String v;

        if (StringUtils.hasText(segment)) {
            v = getPropertyTrimmed(segment + ".internal-token");
            if (StringUtils.hasText(v)) {
                return v;
            }
            if ("users".equals(segment)) {
                v = getPropertyTrimmed("user.internal-token");
                if (StringUtils.hasText(v)) {
                    return v;
                }
            }
        }

        v = getPropertyTrimmed("internal.token");
        if (StringUtils.hasText(v)) {
            return v;
        }

        return "";
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

    private void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(CommonErrorCode.FORBIDDEN.getCode(), message)));
    }
}
