package com.nowcoder.community.auth.config;

import com.nowcoder.community.infra.startup.StartupValidator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class AuthStartupValidator implements StartupValidator {

    @Override
    public void validate(Environment environment, List<String> errors) {
        if (environment == null || errors == null) {
            return;
        }

        String appName = getTrimmed(environment, "spring.application.name");
        if (!"community-app".equalsIgnoreCase(appName)) {
            // 单体应用下才启用 auth 相关 prod 校验（避免误用于其它独立进程）。
            return;
        }

        requireTrue(environment, errors, "security.jwt.refresh-cookie-secure", "生产环境必须 Secure=true（HTTPS），请设置 AUTH_REFRESH_COOKIE_SECURE=true");
        requireOneOf(environment, errors, "security.jwt.refresh-cookie-same-site", List.of("Lax", "Strict", "None"), "请设置 AUTH_REFRESH_COOKIE_SAME_SITE（Lax/Strict/None）");
        requireNonBlank(environment, errors, "auth.password-reset.reset-base-url", "设置环境变量 AUTH_PASSWORD_RESET_BASE_URL（指向公网可访问入口）");
        requireFalse(environment, errors, "auth.password-reset.expose-reset-link", "生产环境禁止回传重置链接，请设置 AUTH_EXPOSE_RESET_LINK=false");
        requireTrue(environment, errors, "auth.registration.mail.enabled", "生产环境必须启用 SMTP 邮件发送，请设置 AUTH_MAIL_ENABLED=true 并配置 spring.mail.*");
        requireNonBlank(environment, errors, "spring.mail.host", "配置 spring.mail.host（SMTP 主机）");

        // dev-only：固定验证码只允许用于本地/联调。prod 下若误开会直接变成漏洞/事故源。
        String fixedCode = getTrimmed(environment, "auth.captcha.fixed-code");
        if (StringUtils.hasText(fixedCode)) {
            errors.add("配置不安全：auth.captcha.fixed-code 已设置（生产环境禁止固定验证码，请删除该配置或仅在 dev profile 使用）");
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

    private void requireFalse(Environment env, List<String> errors, String key, String hint) {
        Boolean v = env == null ? null : env.getProperty(key, Boolean.class, Boolean.FALSE);
        if (v != null && v) {
            errors.add("配置不安全：" + key + "=true（" + hint + "）");
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
