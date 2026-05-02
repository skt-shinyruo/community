package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.ConfirmPasswordResetCommand;
import com.nowcoder.community.auth.application.command.RequestPasswordResetCommand;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.result.PasswordResetRequestResult;
import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.domain.repository.PasswordResetTokenRepository;
import com.nowcoder.community.auth.domain.service.PasswordResetDomainService;
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
public class PasswordResetApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetApplicationService.class);

    private final PasswordResetProperties properties;
    private final PasswordResetTokenRepository tokenStore;
    private final UserCredentialQueryApi userCredentialQueryApi;
    private final UserCredentialActionApi userCredentialActionApi;
    private final MailPort mailService;
    private final CaptchaApplicationService captchaService;
    private final PasswordResetDomainService passwordResetDomainService;

    public PasswordResetApplicationService(
            PasswordResetProperties properties,
            PasswordResetTokenRepository tokenStore,
            UserCredentialQueryApi userCredentialQueryApi,
            UserCredentialActionApi userCredentialActionApi,
            MailPort mailService,
            CaptchaApplicationService captchaService,
            PasswordResetDomainService passwordResetDomainService
    ) {
        this.properties = properties;
        this.tokenStore = tokenStore;
        this.userCredentialQueryApi = userCredentialQueryApi;
        this.userCredentialActionApi = userCredentialActionApi;
        this.mailService = mailService;
        this.captchaService = captchaService;
        this.passwordResetDomainService = passwordResetDomainService;
    }

    public PasswordResetRequestResult requestReset(RequestPasswordResetCommand command) {
        String email = command == null ? null : command.email();
        String captchaId = command == null ? null : command.captchaId();
        String captchaCode = command == null ? null : command.captchaCode();
        passwordResetDomainService.requireResetRequestEmail(email);
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
            return new PasswordResetRequestResult(true, "");
        }

        String token = uuid();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getTtlSeconds()));
        tokenStore.store(token, user.userId(), ttl);

        String resetLink = buildResetLink(resetBaseUrl, token);
        mailService.sendPasswordResetMail(normalizedEmail, resetLink);
        SecurityEventLogger.info(log, "password_reset_request", "success",
                "user.id", user.userId(),
                "masked.email", maskEmail(normalizedEmail));

        return new PasswordResetRequestResult(true, "");
    }

    public boolean confirmReset(ConfirmPasswordResetCommand command) {
        String resetToken = command == null ? null : command.resetToken();
        String newPassword = command == null ? null : command.newPassword();
        String captchaId = command == null ? null : command.captchaId();
        String captchaCode = command == null ? null : command.captchaCode();
        passwordResetDomainService.requireConfirmFields(resetToken, newPassword);
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
        }

        String trimmedPassword = newPassword.trim();
        userCredentialActionApi.validatePasswordPolicy(trimmedPassword);
        UUID userId = tokenStore.consume(resetToken.trim());
        if (userId == null) {
            SecurityEventLogger.info(log, "password_reset_confirm", "denied",
                    "community.reason_code", "invalid_token");
            throw new BusinessException(AuthErrorCode.PASSWORD_RESET_INVALID);
        }

        String normalizedToken = resetToken.trim();
        try {
            userCredentialActionApi.resetPasswordAndRevokeRefreshSessions(userId, trimmedPassword);
        } catch (RuntimeException ex) {
            tokenStore.store(normalizedToken, userId, restoreTtl());
            throw ex;
        }
        SecurityEventLogger.info(log, "password_reset_confirm", "success",
                "user.id", userId);
        return true;
    }

    private Duration restoreTtl() {
        return Duration.ofSeconds(Math.max(60, properties.getTtlSeconds()));
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

}
