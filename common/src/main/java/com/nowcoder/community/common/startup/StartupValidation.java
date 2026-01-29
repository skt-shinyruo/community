package com.nowcoder.community.common.startup;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 生产环境启动期校验（fail-closed）：
 * - 缺失关键配置直接阻断启动（避免 silent fallback）
 * - 输出缺失项清单与修复指引，降低排障成本
 */
public class StartupValidation {

    public void validateOrThrow(Environment environment) {
        if (environment == null) {
            return;
        }
        if (!isProd(environment)) {
            return;
        }

        String appName = getTrimmed(environment, "spring.application.name");
        List<String> errors = new ArrayList<>();

        // 1) JWT HMAC secret：所有服务都依赖（网关/服务验签 + 授权）
        String jwtSecret = getTrimmed(environment, "security.jwt.hmac-secret");
        if (!StringUtils.hasText(jwtSecret)) {
            errors.add("缺失配置：security.jwt.hmac-secret（建议设置环境变量 JWT_HMAC_SECRET 或 <SERVICE>_JWT_HMAC_SECRET，长度 >= 32）");
        } else if (jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            errors.add("配置不安全：security.jwt.hmac-secret 长度不足（建议 >= 32 字节）");
        }

        // 2) internal-token：按服务校验（最小权限 / 防旁路）
        switch (appName) {
            case "auth-service" -> {
                requireNonBlank(environment, errors, "auth.user-client.internal-token", "设置环境变量 USER_INTERNAL_TOKEN（用于 auth -> user internal 调用）");
                requireTrue(environment, errors, "security.jwt.refresh-cookie-secure", "生产环境必须 Secure=true（HTTPS），请设置 AUTH_REFRESH_COOKIE_SECURE=true");
                requireOneOf(environment, errors, "security.jwt.refresh-cookie-same-site", List.of("Lax", "Strict", "None"), "请设置 AUTH_REFRESH_COOKIE_SAME_SITE（Lax/Strict/None）");
            }
            case "user-service" -> {
                requireNonBlank(environment, errors, "user.internal-token", "设置环境变量 USER_INTERNAL_TOKEN（用于 /internal/users/**）");
                requireNonBlank(environment, errors, "user.social-client.internal-token", "设置环境变量 SOCIAL_INTERNAL_TOKEN（用于 user -> social internal 调用）");
            }
            case "content-service" -> {
                requireNonBlank(environment, errors, "content.internal-token", "设置环境变量 CONTENT_INTERNAL_TOKEN（用于 /internal/content/**）");
                requireNonBlank(environment, errors, "clients.user.internal-token", "设置环境变量 USER_INTERNAL_TOKEN（用于 content -> user internal 调用）");
                requireNonBlank(environment, errors, "clients.social.internal-token", "设置环境变量 SOCIAL_INTERNAL_TOKEN（用于 content -> social internal 调用）");
            }
            case "search-service" -> {
                requireNonBlank(environment, errors, "search.internal-token", "设置环境变量 SEARCH_INTERNAL_TOKEN（用于 /internal/search/**）");
                requireNonBlank(environment, errors, "search.content-client.internal-token", "设置环境变量 CONTENT_INTERNAL_TOKEN（用于 search -> content internal 拉取数据）");
            }
            case "social-service" -> requireNonBlank(environment, errors, "social.internal-token", "设置环境变量 SOCIAL_INTERNAL_TOKEN（用于 /internal/social/**）");
            case "analytics-service" -> requireNonBlank(environment, errors, "analytics.internal-token", "设置环境变量 ANALYTICS_INTERNAL_TOKEN（用于 /internal/analytics/**）");
            case "message-service" -> {
                requireNonBlank(environment, errors, "clients.user.internal-token", "设置环境变量 USER_INTERNAL_TOKEN（用于 message -> user internal 调用）");
                requireNonBlank(environment, errors, "clients.social.internal-token", "设置环境变量 SOCIAL_INTERNAL_TOKEN（用于 message -> social internal 调用）");
            }
            case "gateway" -> {
                // 网关作为“运维入口”，需要持有下游 internal-token。
                boolean reindexBlocked = isPathBlocked(environment, "/api/search/internal/reindex");
                if (!reindexBlocked) {
                    requireNonBlank(environment, errors, "SEARCH_INTERNAL_TOKEN", "设置环境变量 SEARCH_INTERNAL_TOKEN（用于 gateway -> search /internal/search/reindex）");
                }
                boolean analyticsEnabled = environment.getProperty("analytics.collect.enabled", Boolean.class, Boolean.TRUE);
                if (analyticsEnabled) {
                    requireNonBlank(environment, errors, "analytics.collect.internal-token", "设置环境变量 ANALYTICS_INTERNAL_TOKEN（用于 gateway -> analytics internal 采集）");
                }
            }
            default -> {
                // 其他模块：只做通用校验，避免误伤未知服务
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[startup-validation] 生产环境启动校验失败（fail-closed），服务拒绝启动。").append('\n');
            sb.append("activeProfiles=").append(Arrays.toString(environment.getActiveProfiles())).append('\n');
            sb.append("applicationName=").append(appName).append('\n');
            sb.append("missingOrInvalid=").append('\n');
            for (String e : errors) {
                sb.append(" - ").append(e).append('\n');
            }
            sb.append("fixGuide=").append('\n');
            sb.append(" - 检查 Nacos 配置是否已发布（prod profile 下应为 required/fail-fast）").append('\n');
            sb.append(" - 检查 deploy/.env 与部署平台 Secret/ConfigMap 是否已注入对应环境变量").append('\n');
            throw new IllegalStateException(sb.toString());
        }
    }

    private boolean isProd(Environment environment) {
        if (environment == null) {
            return false;
        }
        for (String p : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPathBlocked(Environment environment, String path) {
        if (environment == null || !StringUtils.hasText(path)) {
            return false;
        }
        // 1) 标准 YAML list：gateway.rate-limit.blocked-path-patterns[0..N]
        for (int i = 0; i < 32; i++) {
            String v = getTrimmed(environment, "gateway.rate-limit.blocked-path-patterns[" + i + "]");
            if (path.equals(v)) {
                return true;
            }
        }

        // 2) 兼容：单行逗号分隔（便于 Nacos 临时覆盖）
        String configured = getTrimmed(environment, "gateway.rate-limit.blocked-path-patterns");
        if (!StringUtils.hasText(configured)) {
            return false;
        }
        String normalized = configured.replace("[", "").replace("]", "");
        for (String item : normalized.split(",")) {
            if (path.equals(item.trim())) {
                return true;
            }
        }
        return false;
    }

    private void requireNonBlank(Environment env, List<String> errors, String key, String hint) {
        String v = getTrimmed(env, key);
        if (!StringUtils.hasText(v)) {
            errors.add("缺失配置：" + key + "（" + hint + "）");
        }
    }

    private void requireTrue(Environment env, List<String> errors, String key, String hint) {
        Boolean v = env == null ? null : env.getProperty(key, Boolean.class);
        if (v == null || !v) {
            errors.add("配置不安全：" + key + "=false（" + hint + "）");
        }
    }

    private void requireOneOf(Environment env, List<String> errors, String key, List<String> allowed, String hint) {
        String v = getTrimmed(env, key);
        if (!StringUtils.hasText(v)) {
            errors.add("缺失配置：" + key + "（" + hint + "）");
            return;
        }
        for (String a : allowed) {
            if (a.equalsIgnoreCase(v)) {
                return;
            }
        }
        errors.add("配置不合法：" + key + "=" + v + "（允许值=" + allowed + "；" + hint + "）");
    }

    private String getTrimmed(Environment env, String key) {
        if (env == null || !StringUtils.hasText(key)) {
            return "";
        }
        String v = env.getProperty(key);
        return v == null ? "" : v.trim();
    }
}
