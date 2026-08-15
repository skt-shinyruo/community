package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.PasswordResetMailDispatcher;
import com.nowcoder.community.auth.application.port.PasswordResetTransactionCompletion;
import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.config.PasswordResetUrlPolicy;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import com.nowcoder.community.auth.domain.repository.PasswordResetTokenRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class PasswordResetApplicationService {

    public record RequestPasswordResetCommand(
            String email,
            String captchaId,
            String captchaCode,
            String clientIp
    ) {
    }

    public record ConfirmPasswordResetCommand(
            String resetToken,
            String newPassword,
            String captchaId,
            String captchaCode
    ) {
    }

    public record PasswordResetRequestResult(boolean issued) {
    }

    private static final Logger log = LoggerFactory.getLogger(PasswordResetApplicationService.class);
    private static final String RATE_LIMIT_EMAIL_KEY_PREFIX = "auth:pwdreset:req:email:";
    private static final String RATE_LIMIT_DELIVERY_KEY_PREFIX = "auth:pwdreset:req:delivery:";
    private static final String RATE_LIMIT_IP_KEY_PREFIX = "auth:pwdreset:req:ip:";
    private static final Duration CONFIRMATION_LEASE = Duration.ofSeconds(30);
    private static final UUID DUMMY_USER_ID = new UUID(0L, 0L);

    private final PasswordResetProperties properties;
    private final PasswordResetTokenRepository tokenStore;
    private final LoginRateLimitRepository resetRequestRateLimitRepository;
    private final UserCredentialQueryApi userCredentialQueryApi;
    private final UserCredentialActionApi userCredentialActionApi;
    private final PasswordResetMailDispatcher passwordResetMailDispatcher;
    private final PasswordResetTransactionCompletion transactionCompletion;
    private final CaptchaChallengeComponent captchaChallenge;
    private final PasswordResetTokenDeriver passwordResetTokenDeriver;
    private final Clock clock;

    public PasswordResetApplicationService(
            PasswordResetProperties properties,
            PasswordResetTokenRepository tokenStore,
            LoginRateLimitRepository resetRequestRateLimitRepository,
            UserCredentialQueryApi userCredentialQueryApi,
            UserCredentialActionApi userCredentialActionApi,
            PasswordResetMailDispatcher passwordResetMailDispatcher,
            PasswordResetTransactionCompletion transactionCompletion,
            CaptchaChallengeComponent captchaChallenge,
            PasswordResetTokenDeriver passwordResetTokenDeriver,
            Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.tokenStore = Objects.requireNonNull(tokenStore, "tokenStore must not be null");
        this.resetRequestRateLimitRepository = Objects.requireNonNull(
                resetRequestRateLimitRepository, "resetRequestRateLimitRepository must not be null");
        this.userCredentialQueryApi = Objects.requireNonNull(
                userCredentialQueryApi, "userCredentialQueryApi must not be null");
        this.userCredentialActionApi = Objects.requireNonNull(
                userCredentialActionApi, "userCredentialActionApi must not be null");
        this.passwordResetMailDispatcher = Objects.requireNonNull(
                passwordResetMailDispatcher, "passwordResetMailDispatcher must not be null");
        this.transactionCompletion = Objects.requireNonNull(
                transactionCompletion, "transactionCompletion must not be null");
        this.captchaChallenge = Objects.requireNonNull(captchaChallenge, "captchaChallenge must not be null");
        this.passwordResetTokenDeriver = Objects.requireNonNull(
                passwordResetTokenDeriver, "passwordResetTokenDeriver must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public PasswordResetRequestResult requestReset(RequestPasswordResetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String email = command.email();
        String captchaId = command.captchaId();
        String captchaCode = command.captchaCode();
        String clientIp = command.clientIp();
        requireResetRequestEmail(email);
        captchaChallenge.requireValidCaptcha(captchaId, captchaCode);

        // 先做配置校验：避免“部分邮箱成功/部分失败”导致用户枚举；也避免签发 token 后才发现链接无法生成。
        normalizeResetBaseUrlOrThrow();

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        enforceIpRequestRateLimit(clientIp);
        UserCredentialView user = userCredentialQueryApi.findByEmailOrNull(normalizedEmail);
        enforceEmailRequestRateLimit(normalizedEmail);
        boolean deliveryAllowed = acquireDeliveryQuota(user, normalizedEmail);
        boolean deliverable = deliveryAllowed
                && user != null
                && user.userId() != null
                && user.loginAllowed();
        UUID tokenUserId = deliverable ? user.userId() : DUMMY_USER_ID;
        long securityVersion = deliverable ? user.securityVersion() : 0L;
        String deliveryEmail = deliverable
                ? (StringUtils.hasText(user.email()) ? user.email().trim() : normalizedEmail)
                : "";
        UUID deliveryId = UUID.randomUUID();
        PasswordResetTokenDeriver.DeliveryMaterial delivery =
                passwordResetTokenDeriver.deriveDelivery(deliveryId);
        String token = delivery.token();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getTtlSeconds()));
        Instant expiresAt = clock.instant().plus(ttl);
        boolean tokenStored = false;
        try {
            tokenStore.store(token, tokenUserId, securityVersion, ttl);
            tokenStored = true;
            transactionCompletion.afterRollback(() -> cleanupIssuedResetToken(token));
            passwordResetMailDispatcher.dispatch(
                    deliveryId,
                    delivery.derivationKeyId(),
                    delivery.deliveryReference(),
                    deliveryEmail,
                    expiresAt
            );
        } catch (RuntimeException ex) {
            if (tokenStored) {
                cleanupIssuedResetToken(token);
            }
            throw ex;
        }
        if (deliverable) {
            SecurityEventLogger.info(log, "password_reset_request", "success",
                    "user.id", user.userId(),
                    "masked.email", maskEmail(normalizedEmail));
        } else {
            SecurityEventLogger.info(log, "password_reset_request", "skipped",
                    "community.reason_code", "hidden_noop",
                    "masked.email", maskEmail(normalizedEmail));
        }

        return new PasswordResetRequestResult(true);
    }

    public boolean confirmReset(ConfirmPasswordResetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String resetToken = command.resetToken();
        String newPassword = command.newPassword();
        String captchaId = command.captchaId();
        String captchaCode = command.captchaCode();
        requireConfirmFields(resetToken, newPassword);
        captchaChallenge.requireValidCaptcha(captchaId, captchaCode);

        userCredentialActionApi.validatePasswordPolicy(newPassword);
        String normalizedToken = resetToken.trim();
        UUID confirmationLeaseId = UUID.randomUUID();
        PasswordResetTokenRepository.PendingPasswordResetToken pending = tokenStore.beginConfirmation(
                normalizedToken,
                clock.instant().plus(CONFIRMATION_LEASE),
                confirmationLeaseId
        );
        if (pending == null || pending.userId() == null) {
            SecurityEventLogger.info(log, "password_reset_confirm", "denied",
                    "community.reason_code", "invalid_token");
            throw new BusinessException(AuthErrorCode.PASSWORD_RESET_INVALID);
        }

        UUID userId = pending.userId();
        boolean passwordUpdated;
        try {
            passwordUpdated = userCredentialActionApi.updatePasswordIfSecurityVersion(
                    userId,
                    newPassword,
                    pending.securityVersionAtIssue()
            );
        } catch (RuntimeException ex) {
            rollbackConfirmation(normalizedToken, pending, ex);
            throw ex;
        }
        revokeGenerationQuietly(userId, pending.securityVersionAtIssue());
        finishConfirmationQuietly(normalizedToken, pending);
        if (!passwordUpdated) {
            SecurityEventLogger.info(log, "password_reset_confirm", "denied",
                    "community.reason_code", "stale_generation",
                    "user.id", userId);
            throw new BusinessException(AuthErrorCode.PASSWORD_RESET_INVALID);
        }
        SecurityEventLogger.info(log, "password_reset_confirm", "success",
                "user.id", userId);
        return true;
    }

    private void requireResetRequestEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "email 不能为空");
        }
    }

    private void requireConfirmFields(String resetToken, String newPassword) {
        if (!StringUtils.hasText(resetToken) || !StringUtils.hasText(newPassword)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "resetToken/newPassword 不能为空");
        }
    }

    private String normalizeResetBaseUrlOrThrow() {
        try {
            return PasswordResetUrlPolicy.normalizeHttpsBaseUrl(properties.getResetBaseUrl());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "auth.password-reset.reset-base-url 必须配置为安全的 HTTPS URL");
        }
    }

    private void enforceIpRequestRateLimit(String clientIp) {
        if (resetRequestRateLimitRepository == null) {
            return;
        }
        int windowSeconds = Math.max(1, properties.getRequestWindowSeconds());
        int maxRequestsPerIp = properties.getMaxRequestsPerIp();
        String ip = clientIp == null ? "" : clientIp.trim();
        if (maxRequestsPerIp > 0 && StringUtils.hasText(ip)) {
            String ipKey = RATE_LIMIT_IP_KEY_PREFIX + passwordResetTokenDeriver.identifierId("ip", ip);
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
            String emailKey = RATE_LIMIT_EMAIL_KEY_PREFIX
                    + passwordResetTokenDeriver.identifierId("email-request", canonicalQuotaEmail(normalizedEmail));
            int emailCount = resetRequestRateLimitRepository.increment(emailKey, windowSeconds);
            if (emailCount > maxRequestsPerEmail) {
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            }
        }
    }

    private boolean acquireDeliveryQuota(UserCredentialView user, String normalizedEmail) {
        if (resetRequestRateLimitRepository == null || properties.getMaxRequestsPerEmail() <= 0) {
            return true;
        }
        String identity = user != null && user.userId() != null
                ? "user:" + user.userId()
                : "email:" + canonicalQuotaEmail(normalizedEmail);
        String deliveryKey = RATE_LIMIT_DELIVERY_KEY_PREFIX
                + passwordResetTokenDeriver.identifierId("delivery", identity);
        int windowSeconds = Math.max(1, properties.getRequestWindowSeconds());
        return resetRequestRateLimitRepository.increment(deliveryKey, windowSeconds)
                <= properties.getMaxRequestsPerEmail();
    }

    private String canonicalQuotaEmail(String email) {
        String value = email == null ? "" : email.trim();
        String caseFolded = value.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(caseFolded, Normalizer.Form.NFKD);
        StringBuilder canonical = new StringBuilder(decomposed.length());
        decomposed.codePoints()
                .filter(codePoint -> {
                    int type = Character.getType(codePoint);
                    return type != Character.NON_SPACING_MARK
                            && type != Character.COMBINING_SPACING_MARK
                            && type != Character.ENCLOSING_MARK;
                })
                .forEach(canonical::appendCodePoint);
        return canonical.toString();
    }

    private void cleanupIssuedResetToken(String token) {
        try {
            tokenStore.delete(token);
        } catch (RuntimeException cleanupEx) {
            log.warn("[password-reset] failed to cleanup issued token after mail failure: {}", cleanupEx.toString());
        }
    }

    private void rollbackConfirmation(
            String token,
            PasswordResetTokenRepository.PendingPasswordResetToken pending,
            RuntimeException updateFailure
    ) {
        try {
            boolean rolledBack = tokenStore.rollbackConfirmation(
                    token,
                    pending.userId(),
                    pending.securityVersionAtIssue(),
                    pending.confirmationLeaseId()
            );
            if (!rolledBack) {
                log.warn("[password-reset] confirmation lease was no longer owned during rollback");
            }
        } catch (RuntimeException restoreFailure) {
            updateFailure.addSuppressed(restoreFailure);
            log.warn("[password-reset] failed to roll back confirmation lease after credential update failure: {}",
                    restoreFailure.toString());
        }
    }

    private void revokeGenerationQuietly(UUID userId, long securityVersionAtIssue) {
        try {
            tokenStore.revokeGeneration(
                    userId,
                    securityVersionAtIssue,
                    Duration.ofSeconds(Math.max(60, properties.getTtlSeconds()))
            );
        } catch (RuntimeException cleanupEx) {
            log.warn("[password-reset] failed to revoke sibling token generation after password version changed: {}",
                    cleanupEx.toString());
        }
    }

    private void finishConfirmationQuietly(
            String token,
            PasswordResetTokenRepository.PendingPasswordResetToken pending
    ) {
        try {
            boolean finished = tokenStore.finishConfirmation(
                    token,
                    pending.userId(),
                    pending.securityVersionAtIssue(),
                    pending.confirmationLeaseId()
            );
            if (!finished) {
                log.warn("[password-reset] confirmation lease was no longer owned during completion");
            }
        } catch (RuntimeException cleanupEx) {
            log.warn("[password-reset] failed to complete consumed reset token: {}", cleanupEx.toString());
        }
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
