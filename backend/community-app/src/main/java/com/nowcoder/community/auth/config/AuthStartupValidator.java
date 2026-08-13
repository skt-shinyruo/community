package com.nowcoder.community.auth.config;

import com.nowcoder.community.infra.startup.StartupValidator;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AuthStartupValidator implements StartupValidator {

    @Override
    public void validate(Environment environment, List<String> errors) {
        if (environment == null || errors == null) {
            return;
        }

        requireTrue(environment, errors, "security.jwt.refresh-cookie-secure", "生产环境必须 Secure=true（HTTPS），请设置 AUTH_REFRESH_COOKIE_SECURE=true");
        requireOneOf(environment, errors, "security.jwt.refresh-cookie-same-site", List.of("Lax", "Strict", "None"), "请设置 AUTH_REFRESH_COOKIE_SAME_SITE（Lax/Strict/None）");
        validateRefreshTokenBounds(environment, errors);
        validatePasswordResetBaseUrl(environment, errors);
        requireStore(environment, errors, "auth.password-reset.store", "redis");
        requireStore(environment, errors, "auth.captcha.store", "redis");
        validatePasswordResetIdentifierSecret(environment, errors);
        requireTrue(environment, errors, "auth.registration.mail.enabled", "生产环境必须启用 SMTP 邮件发送，请设置 AUTH_MAIL_ENABLED=true 并配置 spring.mail.*");
        validateMailConfiguration(environment, errors);
        validateRegistrationStoresAndBounds(environment, errors);
        validateRegistrationMailLease(environment, errors);
        validateLoginRateLimit(environment, errors);
        validateRedisTimeouts(environment, errors);
        validateOriginGuard(environment, errors);
        validateAbusePreventionQuotas(environment, errors);

        // dev-only：固定验证码只允许用于本地/联调。prod 下若误开会直接变成漏洞/事故源。
        String fixedCode = getTrimmed(environment, "auth.captcha.fixed-code");
        if (StringUtils.hasText(fixedCode)) {
            errors.add("配置不安全：auth.captcha.fixed-code 已设置（生产环境禁止固定验证码，请删除该配置或仅在 dev profile 使用）");
        }
        Boolean exposeRegistrationCode = environment.getProperty("auth.registration.code.expose-code", Boolean.class);
        if (Boolean.TRUE.equals(exposeRegistrationCode)) {
            errors.add("配置不安全：auth.registration.code.expose-code=true（生产环境禁止向响应体暴露注册验证码）");
        }
    }

    private void validateMailConfiguration(Environment environment, List<String> errors) {
        requireNonBlank(environment, errors, "spring.mail.host", "配置 SMTP 主机");
        boundedPositiveInt(environment, errors, "spring.mail.port", 1, 65_535, "端口");
        requireNonBlank(environment, errors, "auth.registration.mail.from", "配置邮件 From 地址");

        String host = getTrimmed(environment, "spring.mail.host").toLowerCase(Locale.ROOT);
        if ("mailhog".equals(host) || host.endsWith(".local")) {
            errors.add("配置不安全：spring.mail.host 仍是开发 SMTP 占位地址");
        }
        String from = getTrimmed(environment, "auth.registration.mail.from").toLowerCase(Locale.ROOT);
        if (from.endsWith(".local")) {
            errors.add("配置不安全：auth.registration.mail.from 仍是 .local 开发地址");
        }

        boolean smtpAuth = Boolean.TRUE.equals(environment.getProperty(
                "spring.mail.properties.mail.smtp.auth", Boolean.class));
        if (smtpAuth) {
            requireNonBlank(environment, errors, "spring.mail.username", "SMTP auth=true 时必须配置用户名");
            requireNonBlank(environment, errors, "spring.mail.password", "SMTP auth=true 时必须通过 Secret 配置密码");
        }

        boolean startTlsRequired = Boolean.TRUE.equals(environment.getProperty(
                "spring.mail.properties.mail.smtp.starttls.required", Boolean.class));
        boolean startTlsEnabled = Boolean.TRUE.equals(environment.getProperty(
                "spring.mail.properties.mail.smtp.starttls.enable", Boolean.class));
        boolean sslEnabled = Boolean.TRUE.equals(environment.getProperty(
                "spring.mail.properties.mail.smtp.ssl.enable", Boolean.class));
        if (!sslEnabled && !(startTlsEnabled && startTlsRequired)) {
            errors.add("配置不安全：生产 SMTP 必须启用隐式 SSL，或同时启用并强制 STARTTLS");
        }
        if (startTlsRequired && !startTlsEnabled) {
            errors.add("配置不合法：spring.mail.properties.mail.smtp.starttls.required=true"
                    + " 时必须同时启用 STARTTLS");
        }
    }

    private void validateOriginGuard(Environment environment, List<String> errors) {
        requireTrue(environment, errors, "gateway.origin-guard.enabled",
                "生产环境认证写入口必须启用 OriginGuard");

        Boolean failOpen = environment.getProperty(
                "gateway.origin-guard.fail-open-when-allowlist-empty", Boolean.class);
        if (failOpen == null || failOpen) {
            errors.add("配置不安全：gateway.origin-guard.fail-open-when-allowlist-empty"
                    + " 必须显式为 false（生产环境必须 fail-closed）");
        }

        String raw = getTrimmed(environment, "gateway.origin-guard.allowed-origins");
        if (!StringUtils.hasText(raw)) {
            errors.add("缺失配置：gateway.origin-guard.allowed-origins"
                    + "（生产环境必须配置至少一个可信 http/https Origin）");
            return;
        }
        String[] origins = raw.split(",", -1);
        for (String candidate : origins) {
            if (!validOrigin(candidate)) {
                errors.add("配置不合法：gateway.origin-guard.allowed-origins"
                        + " 必须是非空、无 userinfo/path/query/fragment 的 http/https Origin 列表");
                return;
            }
        }
    }

    private boolean validOrigin(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        try {
            URI uri = URI.create(candidate.trim());
            String scheme = uri.getScheme();
            boolean supportedScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            boolean emptyPath = !StringUtils.hasText(uri.getPath());
            int port = uri.getPort();
            return supportedScheme
                    && StringUtils.hasText(uri.getHost())
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && emptyPath
                    && (port == -1 || port > 0 && port <= 65_535);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void validateAbusePreventionQuotas(Environment environment, List<String> errors) {
        boundedPositiveInt(environment, errors, "auth.password-reset.ttl-seconds",
                60, 86_400, "秒");
        boundedPositiveInt(environment, errors, "auth.password-reset.request-window-seconds",
                60, 604_800, "秒");
        boundedPositiveInt(environment, errors, "auth.password-reset.max-requests-per-email",
                1, 100, "次");
        boundedPositiveInt(environment, errors, "auth.password-reset.max-requests-per-ip",
                1, 10_000, "次");
        boundedPositiveInt(environment, errors, "auth.captcha.ttl-seconds",
                10, 3_600, "秒");
        boundedPositiveInt(environment, errors, "auth.captcha.max-failures",
                1, 20, "次");
        boundedPositiveInt(environment, errors, "auth.captcha.max-issue-requests-per-ip",
                1, 10_000, "次");
        boundedPositiveInt(environment, errors, "auth.registration.request-limit.window-seconds",
                60, 604_800, "秒");
        boundedPositiveInt(environment, errors, "auth.registration.request-limit.max-requests-per-username",
                1, 100, "次");
        boundedPositiveInt(environment, errors, "auth.registration.request-limit.max-requests-per-email",
                1, 100, "次");
        boundedPositiveInt(environment, errors, "auth.registration.request-limit.max-requests-per-ip",
                1, 10_000, "次");
        boundedPositiveInt(environment, errors, "auth.registration.resend-limit.window-seconds",
                60, 604_800, "秒");
        boundedPositiveInt(environment, errors, "auth.registration.resend-limit.max-requests-per-registration",
                1, 100, "次");
        boundedPositiveInt(environment, errors, "auth.registration.resend-limit.max-requests-per-email",
                1, 100, "次");
        boundedPositiveInt(environment, errors, "auth.registration.resend-limit.max-requests-per-ip",
                1, 10_000, "次");
    }

    private void validateRefreshTokenBounds(Environment environment, List<String> errors) {
        long refreshTtl = boundedLong(
                environment,
                errors,
                "security.jwt.refresh-token-ttl-seconds",
                300L,
                2_592_000L,
                "秒"
        );
        long reuseGrace = boundedLong(
                environment,
                errors,
                "security.jwt.refresh-reuse-grace-seconds",
                0L,
                300L,
                "秒"
        );
        if (refreshTtl > 0L && reuseGrace >= refreshTtl) {
            errors.add("配置不合法：security.jwt.refresh-reuse-grace-seconds"
                    + " 必须小于 security.jwt.refresh-token-ttl-seconds");
        }
    }

    private void validatePasswordResetIdentifierSecret(Environment environment, List<String> errors) {
        String identifierSecret = getTrimmed(environment, "auth.password-reset.identifier-hmac-secret");
        if (!StringUtils.hasText(identifierSecret)) {
            errors.add("缺失配置：auth.password-reset.identifier-hmac-secret"
                    + "（设置独立的 AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET，长度 >= 32 字节）");
            return;
        }
        if (identifierSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            errors.add("配置不安全：auth.password-reset.identifier-hmac-secret 长度不足（建议 >= 32 字节）");
        }
        if (isDevelopmentSecret(identifierSecret)) {
            errors.add("配置不安全：auth.password-reset.identifier-hmac-secret 仍是仓库示例或开发占位密钥");
        }

        String serviceJwtSecret = getTrimmed(environment, "security.jwt.service-hmac-secret");
        if (StringUtils.hasText(serviceJwtSecret) && identifierSecret.equals(serviceJwtSecret)) {
            errors.add("配置不安全：auth.password-reset.identifier-hmac-secret"
                    + " 必须与 security.jwt.service-hmac-secret 使用不同密钥");
        }

        Set<String> seen = new HashSet<>();
        seen.add(identifierSecret);
        for (String previous : bindStringList(
                environment, errors, "auth.password-reset.previous-identifier-hmac-secrets")) {
            if (previous.getBytes(StandardCharsets.UTF_8).length < 32) {
                errors.add("配置不安全：auth.password-reset.previous-identifier-hmac-secrets"
                        + " 包含长度不足的辅助密钥（每项必须 >= 32 字节）");
            }
            if (isDevelopmentSecret(previous)) {
                errors.add("配置不安全：auth.password-reset.previous-identifier-hmac-secrets"
                        + " 包含仓库示例或开发占位密钥");
            }
            if (!seen.add(previous)) {
                errors.add("配置不合法：auth.password-reset.previous-identifier-hmac-secrets"
                        + " 不得包含当前密钥或重复项");
            }
            if (StringUtils.hasText(serviceJwtSecret) && previous.equals(serviceJwtSecret)) {
                errors.add("配置不安全：auth.password-reset.previous-identifier-hmac-secrets"
                        + " 不得复用 security.jwt.service-hmac-secret");
            }
        }

        String quotaSecret = getTrimmed(environment, "auth.password-reset.quota-hmac-secret");
        if (!StringUtils.hasText(quotaSecret)) {
            errors.add("缺失配置：auth.password-reset.quota-hmac-secret"
                    + "（设置稳定且独立的 AUTH_PASSWORD_RESET_QUOTA_HMAC_SECRET，长度 >= 32 字节）");
        } else {
            if (quotaSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                errors.add("配置不安全：auth.password-reset.quota-hmac-secret 长度不足（建议 >= 32 字节）");
            }
            if (isDevelopmentSecret(quotaSecret)) {
                errors.add("配置不安全：auth.password-reset.quota-hmac-secret 仍是仓库示例或开发占位密钥");
            }
            if (quotaSecret.equals(identifierSecret)) {
                errors.add("配置不安全：auth.password-reset.quota-hmac-secret"
                        + " 必须与 identifier-hmac-secret 使用不同密钥");
            }
            if (StringUtils.hasText(serviceJwtSecret) && quotaSecret.equals(serviceJwtSecret)) {
                errors.add("配置不安全：auth.password-reset.quota-hmac-secret"
                        + " 不得复用 security.jwt.service-hmac-secret");
            }
        }
    }

    private void validateRegistrationStoresAndBounds(Environment environment, List<String> errors) {
        requireStore(environment, errors, "auth.registration.code.store", "redis");
        requireStore(environment, errors, "auth.registration.draft.store", "redis");

        int codeTtl = boundedPositiveInt(environment, errors,
                "auth.registration.code.ttl-seconds", 120, 1_800, "秒");
        boundedPositiveInt(environment, errors,
                "auth.registration.code.max-failures", 1, 10, "次");
        int cooldown = boundedPositiveInt(environment, errors,
                "auth.registration.code.resend-cooldown-seconds", 15, 600, "秒");
        int draftTtl = boundedPositiveInt(environment, errors,
                "auth.registration.draft.ttl-seconds", 300, 86_400, "秒");

        if (codeTtl > 0 && cooldown > codeTtl) {
            errors.add("配置不合法：auth.registration.code.resend-cooldown-seconds"
                    + " 不得大于 auth.registration.code.ttl-seconds");
        }
        if (codeTtl > 0 && draftTtl > 0 && draftTtl < codeTtl) {
            errors.add("配置不合法：auth.registration.draft.ttl-seconds"
                    + " 必须大于等于 auth.registration.code.ttl-seconds");
        }
    }

    private void validateLoginRateLimit(Environment environment, List<String> errors) {
        requireTrue(environment, errors, "auth.login-rate-limit.enabled",
                "生产环境必须启用登录风控");
        boundedPositiveInt(environment, errors,
                "auth.login-rate-limit.window-seconds", 10, 3_600, "秒");
        int ipLimit = boundedPositiveInt(environment, errors,
                "auth.login-rate-limit.max-failures-per-ip", 1, 1_000, "次");
        int userLimit = boundedPositiveInt(environment, errors,
                "auth.login-rate-limit.max-failures-per-user", 1, 50, "次");
        int ipCaptcha = boundedPositiveInt(environment, errors,
                "auth.login-rate-limit.captcha-required-failures-per-ip", 1, 1_000, "次");
        int userCaptcha = boundedPositiveInt(environment, errors,
                "auth.login-rate-limit.captcha-required-failures-per-user", 1, 50, "次");
        boundedPositiveInt(environment, errors,
                "auth.login-rate-limit.password-check-lease-seconds", 30, 600, "秒");

        if (ipLimit > 0 && ipCaptcha > ipLimit) {
            errors.add("配置不合法：auth.login-rate-limit.captcha-required-failures-per-ip"
                    + " 不得大于 max-failures-per-ip");
        }
        if (userLimit > 0 && userCaptcha > userLimit) {
            errors.add("配置不合法：auth.login-rate-limit.captcha-required-failures-per-user"
                    + " 不得大于 max-failures-per-user");
        }
    }

    private void validateRedisTimeouts(Environment environment, List<String> errors) {
        boundedPositiveDurationMillis(
                environment, errors, "spring.data.redis.connect-timeout", 5_000L);
        long commandTimeoutMs = boundedPositiveDurationMillis(
                environment, errors, "spring.data.redis.timeout", 5_000L);
        Integer leaseSeconds = environment.getProperty(
                "auth.login-rate-limit.password-check-lease-seconds", Integer.class);
        if (commandTimeoutMs > 0L && leaseSeconds != null && leaseSeconds > 0) {
            long renewalIntervalMs = leaseSeconds * 1_000L / 4L;
            if (commandTimeoutMs >= renewalIntervalMs) {
                errors.add("配置不安全：spring.data.redis.timeout 必须小于登录风控租约续租间隔");
            }
        }
    }

    private void validateRegistrationMailLease(Environment environment, List<String> errors) {
        int connectionTimeout = positiveInt(environment, errors,
                "spring.mail.properties.mail.smtp.connectiontimeout", 30_000);
        int readTimeout = positiveInt(environment, errors,
                "spring.mail.properties.mail.smtp.timeout", 30_000);
        int writeTimeout = positiveInt(environment, errors,
                "spring.mail.properties.mail.smtp.writetimeout", 30_000);
        Integer leaseSeconds = environment.getProperty(
                "auth.registration.code.operation-lease-seconds", Integer.class);
        if (leaseSeconds == null || leaseSeconds < 60 || leaseSeconds > 600) {
            errors.add("配置不安全：auth.registration.code.operation-lease-seconds"
                    + " 必须在 60..600 秒内，且覆盖 SMTP 最坏耗时");
            return;
        }
        long timeoutBudgetMs = (long) connectionTimeout + readTimeout + writeTimeout + 10_000L;
        if (connectionTimeout > 0 && readTimeout > 0 && writeTimeout > 0
                && leaseSeconds * 1_000L <= timeoutBudgetMs) {
            errors.add("配置不安全：auth.registration.code.operation-lease-seconds"
                    + " 必须大于 SMTP connection/read/write timeout 总和加 10 秒余量");
        }
        Integer codeTtl = environment.getProperty("auth.registration.code.ttl-seconds", Integer.class);
        Integer draftTtl = environment.getProperty("auth.registration.draft.ttl-seconds", Integer.class);
        if (codeTtl != null && leaseSeconds >= codeTtl) {
            errors.add("配置不合法：auth.registration.code.operation-lease-seconds"
                    + " 必须小于 auth.registration.code.ttl-seconds");
        }
        if (draftTtl != null && leaseSeconds >= draftTtl) {
            errors.add("配置不合法：auth.registration.code.operation-lease-seconds"
                    + " 必须小于 auth.registration.draft.ttl-seconds");
        }
    }

    private void requireStore(Environment environment, List<String> errors, String key, String expected) {
        String actual = getTrimmed(environment, key);
        if (!expected.equalsIgnoreCase(actual)) {
            errors.add("配置不安全：" + key + "=" + actual + "（生产环境必须使用 " + expected + "）");
        }
    }

    private List<String> bindStringList(Environment environment, List<String> errors, String key) {
        try {
            return Binder.get(environment)
                    .bind(key, Bindable.listOf(String.class))
                    .orElse(List.of())
                    .stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
        } catch (RuntimeException exception) {
            errors.add("配置不合法：" + key + " 无法解析为字符串列表");
            return List.of();
        }
    }

    private boolean isDevelopmentSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("dev-")
                || normalized.contains("change-before")
                || normalized.contains("change-me")
                || normalized.contains("changeme")
                || normalized.contains("replace-me")
                || normalized.contains("example-secret");
    }

    private int positiveInt(Environment environment, List<String> errors, String key, int maximum) {
        Integer value = environment.getProperty(key, Integer.class);
        if (value == null || value <= 0 || value > maximum) {
            errors.add("配置不安全：" + key + " 必须在 1.." + maximum + " 毫秒范围内");
            return 0;
        }
        return value;
    }

    private long boundedPositiveDurationMillis(
            Environment environment,
            List<String> errors,
            String key,
            long maximumMillis
    ) {
        try {
            Duration value = Binder.get(environment)
                    .bind(key, Bindable.of(Duration.class))
                    .orElse(null);
            if (value == null || value.isZero() || value.isNegative()) {
                errors.add("配置不安全：" + key + " 必须大于 0 且不超过 " + maximumMillis + " 毫秒");
                return 0L;
            }
            long millis = value.toMillis();
            if (millis <= 0L || millis > maximumMillis) {
                errors.add("配置不安全：" + key + " 必须大于 0 且不超过 " + maximumMillis + " 毫秒");
            }
            return millis;
        } catch (RuntimeException exception) {
            errors.add("配置不合法：" + key + " 无法解析为 duration");
            return 0L;
        }
    }

    private int boundedPositiveInt(
            Environment environment,
            List<String> errors,
            String key,
            int minimum,
            int maximum,
            String unit
    ) {
        Integer value = environment.getProperty(key, Integer.class);
        if (value == null || value < minimum || value > maximum) {
            errors.add("配置不安全：" + key + " 必须在 " + minimum + ".." + maximum + " " + unit + "范围内");
            return 0;
        }
        return value;
    }

    private long boundedLong(
            Environment environment,
            List<String> errors,
            String key,
            long minimum,
            long maximum,
            String unit
    ) {
        Long value = environment.getProperty(key, Long.class);
        if (value == null || value < minimum || value > maximum) {
            errors.add("配置不安全：" + key + " 必须在 " + minimum + ".." + maximum + " " + unit + "范围内");
            return 0L;
        }
        return value;
    }

    private void validatePasswordResetBaseUrl(Environment environment, List<String> errors) {
        String baseUrl = getTrimmed(environment, "auth.password-reset.reset-base-url");
        try {
            PasswordResetUrlPolicy.normalizeHttpsBaseUrl(baseUrl);
        } catch (IllegalArgumentException exception) {
            errors.add("配置不合法：auth.password-reset.reset-base-url"
                    + "（必须是无 userinfo/query/fragment 的绝对 HTTPS URL，请设置 AUTH_PASSWORD_RESET_BASE_URL）");
        }
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
