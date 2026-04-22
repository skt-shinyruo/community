package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.dto.RegisterCodeResendResponse;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.auth.logging.SecurityEventLogger;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PendingRegistrationUserView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserPendingRegistrationQueryApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@Service
public class RegistrationVerificationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationVerificationService.class);

    private final UserPendingRegistrationQueryApi userPendingRegistrationQueryApi;
    private final UserRegistrationActionApi userRegistrationActionApi;
    private final RegistrationProperties properties;
    private final RegistrationCodeStore registrationCodeStore;
    private final MailService mailService;
    private final CaptchaService captchaService;
    private final RegistrationSessionStore registrationSessionStore;
    private final AuthService authService;

    public RegistrationVerificationService(
            UserPendingRegistrationQueryApi userPendingRegistrationQueryApi,
            UserRegistrationActionApi userRegistrationActionApi,
            RegistrationProperties properties,
            RegistrationCodeStore registrationCodeStore,
            MailService mailService,
            CaptchaService captchaService,
            RegistrationSessionStore registrationSessionStore,
            AuthService authService
    ) {
        this.userPendingRegistrationQueryApi = userPendingRegistrationQueryApi;
        this.userRegistrationActionApi = userRegistrationActionApi;
        this.properties = properties;
        this.registrationCodeStore = registrationCodeStore;
        this.mailService = mailService;
        this.captchaService = captchaService;
        this.registrationSessionStore = registrationSessionStore;
        this.authService = authService;
    }

    public RegisterCodeResendResponse resendCode(String registrationToken, String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
        }

        UUID userId = resolveUserIdOrThrow(registrationToken);
        PendingRegistrationUserView user = requirePendingUser(userId);

        String code = generateCode();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getCode().getTtlSeconds()));
        Duration cooldown = Duration.ofSeconds(Math.max(0, properties.getCode().getResendCooldownSeconds()));
        RegistrationCodeStore.IssueResult issueResult = registrationCodeStore.issue(user.userId(), code, ttl, cooldown);
        if (issueResult == RegistrationCodeStore.IssueResult.COOLDOWN_ACTIVE) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_RESEND_COOLDOWN);
        }
        mailService.sendRegistrationCodeMail(user.email(), code);

        RegisterCodeResendResponse response = new RegisterCodeResendResponse();
        response.setIssued(true);
        response.setMaskedEmail(maskEmail(user.email()));
        if (properties.getCode().isExposeCode()) {
            response.setDebugEmailCode(code);
        }
        return response;
    }

    public AuthService.LoginResult verifyAndLogin(String registrationToken, String code) {
        if (!StringUtils.hasText(registrationToken) || !StringUtils.hasText(code)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "registrationToken/code 不能为空");
        }

        UUID userId = resolveUserIdOrThrow(registrationToken);

        PendingRegistrationUserView user = requirePendingUser(userId);
        RegistrationCodeStore.VerifyResult result = registrationCodeStore.verifyAndConsume(userId, code.trim());
        if (result == RegistrationCodeStore.VerifyResult.SUCCESS) {
            UserCredentialView activatedUser = userRegistrationActionApi.activatePendingUser(userId);
            if (activatedUser == null || activatedUser.userId() == null) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "激活用户失败");
            }
            AuthService.LoginResult loginResult = authService.issueLoginResult(activatedUser);
            SecurityEventLogger.info(log, "registration_verify", "success",
                    "user.id", activatedUser.userId(),
                    "username", activatedUser.username());
            try {
                registrationSessionStore.delete(registrationToken);
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
            return loginResult;
        }
        if (result == RegistrationCodeStore.VerifyResult.EXPIRED) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_EXPIRED);
        }
        if (result == RegistrationCodeStore.VerifyResult.TOO_MANY_ATTEMPTS) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_TOO_MANY_ATTEMPTS);
        }
        throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_INVALID);
    }

    private UUID resolveUserIdOrThrow(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "registrationToken 不能为空");
        }
        UUID userId = registrationSessionStore == null ? null : registrationSessionStore.findUserId(registrationToken.trim());
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);
        }
        return userId;
    }

    private PendingRegistrationUserView requirePendingUser(UUID userId) {
        Duration pendingUserTtl = Duration.ofSeconds(Math.max(60, properties.getPendingUser().getTtlSeconds()));
        PendingRegistrationUserView user = userPendingRegistrationQueryApi.getPendingUser(userId, pendingUserTtl);
        if (user.status() != 0) {
            throw new BusinessException(AuthErrorCode.USER_DISABLED, "账号已激活，请直接登录");
        }
        return user;
    }

    private String generateCode() {
        return Integer.toString(ThreadLocalRandom.current().nextInt(100000, 1000000));
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
