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
import java.util.ArrayList;
import java.util.List;

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
        if (!StringUtils.hasText(segment) || !isValidSegment(segment)) {
            forbidden(resp, "internal 路径不合法");
            return;
        }
        List<String> expectedTokens = resolveExpectedTokens(segment, path);
        if (expectedTokens.isEmpty()) {
            forbidden(resp, "internal-token 未配置");
            return;
        }

        String token = req.getHeader(HEADER_INTERNAL_TOKEN);
        if (!StringUtils.hasText(token) || !matchesAny(token, expectedTokens)) {
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

    private List<String> resolveExpectedTokens(String segment, String path) {
        // 1) service 专用 token：<segment>.internal-token（例如 content.internal-token）
        // 2) alias：users -> user.internal-token（历史/命名兼容）
        //
        // 轮转支持：
        // - <segment>.internal-token-previous / user.internal-token-previous
        //
        // 说明：轮转窗口内允许 current/previous 两个 token 同时生效，避免升级时调用中断。

        List<String> tokens = new ArrayList<>(4);

        if (StringUtils.hasText(segment)) {
            // 最小权限分域（user-service 内部高权限写接口）：
            // - /internal/users/{id}/password
            // - /internal/users/{id}/moderation
            //
            // 这些入口禁止回退到 users.internal-token（避免“通用 token 泄露扩大爆炸半径”）。
            if ("users".equals(segment) && isUserOpsEndpoint(path)) {
                addIfPresent(tokens, "users.ops.internal-token");
                addIfPresent(tokens, "users.ops.internal-token-previous");
                addIfPresent(tokens, "user.ops.internal-token");
                addIfPresent(tokens, "user.ops.internal-token-previous");
                return tokens;
            }

            addIfPresent(tokens, segment + ".internal-token");
            addIfPresent(tokens, segment + ".internal-token-previous");
            if ("users".equals(segment)) {
                addIfPresent(tokens, "user.internal-token");
                addIfPresent(tokens, "user.internal-token-previous");
            }
        }
        return tokens;
    }

    private boolean isUserOpsEndpoint(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        // 精确匹配关键写入口，避免误伤 users 读路径。
        // /internal/users/{userId}/password
        // /internal/users/{userId}/moderation
        return path.matches("^/internal/users/\\d+/password$")
                || path.matches("^/internal/users/\\d+/moderation$");
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

    private void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(CommonErrorCode.FORBIDDEN.getCode(), message)));
    }
}
