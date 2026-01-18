package com.nowcoder.community.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * legacy-community 迁移期保护：
 * - 可选：将旧 Thymeleaf 页面入口重定向到 Vue3 SPA（/index、/discuss/detail/{id}、/user/profile/{id}）
 * - 可选：对旧写入口做只读保护（POST/PUT/PATCH/DELETE 返回 410）
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LegacyMigrationFilter implements Filter {

    private static final Pattern DISCUSS_DETAIL = Pattern.compile("^/discuss/detail/(\\d+)$");
    private static final Pattern USER_PROFILE = Pattern.compile("^/user/profile/(\\d+)$");

    private final boolean redirectEnabled;
    private final boolean readonlyEnabled;

    public LegacyMigrationFilter(
            @Value("${legacy.migration.redirect-enabled:true}") boolean redirectEnabled,
            @Value("${legacy.readonly.enabled:true}") boolean readonlyEnabled
    ) {
        this.redirectEnabled = redirectEnabled;
        this.readonlyEnabled = readonlyEnabled;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI();
        String method = req.getMethod();

        if (redirectEnabled && HttpMethod.GET.matches(method)) {
            if ("/index".equals(path) || "/".equals(path)) {
                resp.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                resp.setHeader("Location", "/#/posts");
                return;
            }

            Matcher discuss = DISCUSS_DETAIL.matcher(path);
            if (discuss.matches()) {
                resp.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                resp.setHeader("Location", "/#/posts/" + discuss.group(1));
                return;
            }

            Matcher profile = USER_PROFILE.matcher(path);
            if (profile.matches()) {
                resp.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                resp.setHeader("Location", "/#/users/" + profile.group(1));
                return;
            }
        }

        if (readonlyEnabled && isWriteMethod(method) && isLegacyWriteScope(path)) {
            resp.setStatus(HttpServletResponse.SC_GONE);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":410,\"message\":\"legacy-community 已进入只读/迁移模式，请使用 /api/** 新接口\",\"data\":null}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isWriteMethod(String method) {
        return HttpMethod.POST.matches(method)
                || HttpMethod.PUT.matches(method)
                || HttpMethod.PATCH.matches(method)
                || HttpMethod.DELETE.matches(method);
    }

    private boolean isLegacyWriteScope(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/discuss")
                || path.startsWith("/comment")
                || path.startsWith("/user");
    }
}

