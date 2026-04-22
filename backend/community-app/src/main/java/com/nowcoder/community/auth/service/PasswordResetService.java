package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.auth.logging.SecurityEventLogger;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.action.UserCredentialActionApi;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetProperties properties;
    private final PasswordResetTokenStore tokenStore;
    private final UserCredentialQueryApi userCredentialQueryApi;
    private final UserCredentialActionApi userCredentialActionApi;
    private final MailService mailService;
    private final CaptchaService captchaService;

    public PasswordResetService(
            PasswordResetProperties properties,
            PasswordResetTokenStore tokenStore,
            UserCredentialQueryApi userCredentialQueryApi,
            UserCredentialActionApi userCredentialActionApi,
            MailService mailService,
            CaptchaService captchaService
    ) {
        this.properties = properties;
        this.tokenStore = tokenStore;
        this.userCredentialQueryApi = userCredentialQueryApi;
        this.userCredentialActionApi = userCredentialActionApi;
        this.mailService = mailService;
        this.captchaService = captchaService;
    }

    public RequestResult requestReset(String email, String captchaId, String captchaCode) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "email 不能为空");
        }
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
        }

        // 先做配置校验：避免“部分邮箱成功/部分失败”导致用户枚举；也避免签发 token 后才发现链接无法生成。
        String resetBaseUrl = normalizeResetBaseUrlOrThrow();

        String normalizedEmail = email.trim();
        UserCredentialView user = userCredentialQueryApi.findByEmailOrNull(normalizedEmail);
        if (user == null || user.userId() == null || user.status() == 0) {
            // 防用户枚举：邮箱不存在/未激活等情况也返回“已发送”（但不实际下发 token/邮件）
            SecurityEventLogger.info(log, "password_reset_request", "skipped",
                    "community.reason_code", "hidden_noop",
                    "masked.email", maskEmail(normalizedEmail));
            return new RequestResult(true, "");
        }

        String token = uuid();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getTtlSeconds()));
        tokenStore.store(token, user.userId(), ttl);

        String resetLink = buildResetLink(resetBaseUrl, token);
        mailService.sendPasswordResetMail(normalizedEmail, resetLink);
        SecurityEventLogger.info(log, "password_reset_request", "success",
                "user.id", user.userId(),
                "masked.email", maskEmail(normalizedEmail));

        if (properties.isExposeResetLink()) {
            return new RequestResult(true, resetLink);
        }
        return new RequestResult(true, "");
    }

    public boolean confirmReset(String resetToken, String newPassword, String captchaId, String captchaCode) {
        if (!StringUtils.hasText(resetToken) || !StringUtils.hasText(newPassword)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "resetToken/newPassword 不能为空");
        }
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
        }

        UUID userId = tokenStore.consume(resetToken.trim());
        if (userId == null) {
            SecurityEventLogger.info(log, "password_reset_confirm", "denied",
                    "community.reason_code", "invalid_token");
            throw new BusinessException(AuthErrorCode.PASSWORD_RESET_INVALID);
        }

        userCredentialActionApi.updatePassword(userId, newPassword.trim());
        SecurityEventLogger.info(log, "password_reset_confirm", "success",
                "user.id", userId);
        return true;
    }

    private String normalizeResetBaseUrlOrThrow() {
        String base = properties.getResetBaseUrl();
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "未配置 auth.password-reset.reset-base-url，无法生成重置密码链接");
        }
        String normalized = base.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String buildResetLink(String resetBaseUrl, String token) {
        return resetBaseUrl + "/#/auth/password/reset?token=" + token;
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String maskEmail(String email) {
        String normalized = email == null ? "" : email.trim();
        int at = normalized.indexOf('@');
        if (at <= 0) {
            return normalized;
        }
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        if (local.length() == 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    public record RequestResult(boolean issued, String resetLink) {
    }
}
