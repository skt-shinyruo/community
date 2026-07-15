package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.ConfirmPasswordResetCommand;
import com.nowcoder.community.auth.application.command.RequestPasswordResetCommand;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.result.PasswordResetRequestResult;
import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import com.nowcoder.community.auth.domain.repository.PasswordResetTokenRepository;
import com.nowcoder.community.auth.domain.service.AuthSecretGenerator;
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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class PasswordResetApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetApplicationService.class);
    private static final String RATE_LIMIT_EMAIL_KEY_PREFIX = "auth:pwdreset:req:email:";
    private static final String RATE_LIMIT_IP_KEY_PREFIX = "auth:pwdreset:req:ip:";

    private final PasswordResetProperties properties;
    private final PasswordResetTokenRepository tokenStore;
    private final LoginRateLimitRepository resetRequestRateLimitRepository;
    private final UserCredentialQueryApi userCredentialQueryApi;
    private final UserCredentialActionApi userCredentialActionApi;
    private final MailPort mailService;
    private final CaptchaChallengeComponent captchaChallenge;
    private final AuthSecretGenerator authSecretGenerator;
    private final PasswordResetDomainService passwordResetDomainService;

    public PasswordResetApplicationService(
            PasswordResetProperties properties,
            PasswordResetTokenRepository tokenStore,
            LoginRateLimitRepository resetRequestRateLimitRepository,
            UserCredentialQueryApi userCredentialQueryApi,
            UserCredentialActionApi userCredentialActionApi,
            MailPort mailService,
            CaptchaChallengeComponent captchaChallenge,
            AuthSecretGenerator authSecretGenerator,
            PasswordResetDomainService passwordResetDomainService
    ) {
        this.properties = properties;
        this.tokenStore = tokenStore;
        this.resetRequestRateLimitRepository = resetRequestRateLimitRepository;
        this.userCredentialQueryApi = userCredentialQueryApi;
        this.userCredentialActionApi = userCredentialActionApi;
        this.mailService = mailService;
        this.captchaChallenge = captchaChallenge;
        this.authSecretGenerator = authSecretGenerator;
        this.passwordResetDomainService = passwordResetDomainService;
    }

    public PasswordResetRequestResult requestReset(RequestPasswordResetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String email = command.email();
        String captchaId = command.captchaId();
        String captchaCode = command.captchaCode();
        String clientIp = command.clientIp();
        passwordResetDomainService.requireResetRequestEmail(email);
        captchaChallenge.requireValidCaptcha(captchaId, captchaCode);

        // 先做配置校验：避免“部分邮箱成功/部分失败”导致用户枚举；也避免签发 token 后才发现链接无法生成。
        String resetBaseUrl = normalizeResetBaseUrlOrThrow();

        String normalizedEmail = email.trim();
        enforceIpRequestRateLimit(clientIp);
        UserCredentialView user = userCredentialQueryApi.findByEmailOrNull(normalizedEmail);
        if (user == null || user.userId() == null || !user.loginAllowed()) {
            // 防用户枚举：邮箱不存在/未激活等情况也返回“已发送”（但不实际下发 token/邮件）
            SecurityEventLogger.info(log, "password_reset_request", "skipped",
                    "community.reason_code", "hidden_noop",
                    "masked.email", maskEmail(normalizedEmail));
            return new PasswordResetRequestResult(true);
        }
        enforceEmailRequestRateLimit(normalizedEmail);

        String token = newResetToken();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getTtlSeconds()));
        boolean tokenStored = false;
        try {
            tokenStore.store(token, user.userId(), ttl);
            tokenStored = true;
            String resetLink = buildResetLink(resetBaseUrl, token);
            mailService.sendPasswordResetMail(normalizedEmail, resetLink);
        } catch (RuntimeException ex) {
            if (tokenStored) {
                cleanupIssuedResetToken(token);
            }
            throw ex;
        }
        SecurityEventLogger.info(log, "password_reset_request", "success",
                "user.id", user.userId(),
                "masked.email", maskEmail(normalizedEmail));

        return new PasswordResetRequestResult(true);
    }

    public boolean confirmReset(ConfirmPasswordResetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String resetToken = command.resetToken();
        String newPassword = command.newPassword();
        String captchaId = command.captchaId();
        String captchaCode = command.captchaCode();
        passwordResetDomainService.requireConfirmFields(resetToken, newPassword);
        captchaChallenge.requireValidCaptcha(captchaId, captchaCode);

        userCredentialActionApi.validatePasswordPolicy(newPassword);
        String normalizedToken = resetToken.trim();
        PasswordResetTokenRepository.ConsumedPasswordResetToken consumed = tokenStore.consumeWithTtl(normalizedToken);
        if (consumed == null || consumed.userId() == null) {
            SecurityEventLogger.info(log, "password_reset_confirm", "denied",
                    "community.reason_code", "invalid_token");
            throw new BusinessException(AuthErrorCode.PASSWORD_RESET_INVALID);
        }

        UUID userId = consumed.userId();
        try {
            userCredentialActionApi.updatePassword(userId, newPassword);
        } catch (RuntimeException ex) {
            tokenStore.store(normalizedToken, userId, restoreTtl(consumed.remainingTtl()));
            throw ex;
        }
        SecurityEventLogger.info(log, "password_reset_confirm", "success",
                "user.id", userId);
        return true;
    }

    private Duration restoreTtl(Duration remainingTtl) {
        if (remainingTtl != null && !remainingTtl.isNegative() && !remainingTtl.isZero()) {
            return remainingTtl;
        }
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

    private void enforceIpRequestRateLimit(String clientIp) {
        if (resetRequestRateLimitRepository == null) {
            return;
        }
        int windowSeconds = Math.max(1, properties.getRequestWindowSeconds());
        int maxRequestsPerIp = properties.getMaxRequestsPerIp();
        String ip = clientIp == null ? "" : clientIp.trim();
        if (maxRequestsPerIp > 0 && StringUtils.hasText(ip)) {
            String ipKey = RATE_LIMIT_IP_KEY_PREFIX + ip;
            int ipCount = resetRequestRateLimitRepository.increment(ipKey, windowSeconds);
            if (ipCount > maxRequestsPerIp) {
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            }
        }
    }

    private void enforceEmailRequestRateLimit(String normalizedEmail) {
        if (resetRequestRateLimitRepository == null) {
            return;
        }
        int maxRequestsPerEmail = properties.getMaxRequestsPerEmail();
        if (maxRequestsPerEmail > 0 && StringUtils.hasText(normalizedEmail)) {
            int windowSeconds = Math.max(1, properties.getRequestWindowSeconds());
            String emailKey = RATE_LIMIT_EMAIL_KEY_PREFIX + normalizedEmail.toLowerCase(Locale.ROOT);
            int emailCount = resetRequestRateLimitRepository.increment(emailKey, windowSeconds);
            if (emailCount > maxRequestsPerEmail) {
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            }
        }
    }

    private void cleanupIssuedResetToken(String token) {
        try {
            tokenStore.delete(token);
        } catch (RuntimeException cleanupEx) {
            log.warn("[password-reset] failed to cleanup issued token after mail failure: {}", cleanupEx.toString());
        }
    }

    private String newResetToken() {
        return authSecretGenerator.opaqueToken();
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
