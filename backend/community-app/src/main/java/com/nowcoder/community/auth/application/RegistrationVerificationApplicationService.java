package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.ResendRegisterCodeCommand;
import com.nowcoder.community.auth.application.command.VerifyRegisterCodeCommand;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.RegisterCodeResendResult;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import com.nowcoder.community.auth.domain.repository.RegistrationSessionRepository;
import com.nowcoder.community.auth.domain.service.RegistrationDomainService;
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
public class RegistrationVerificationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationVerificationApplicationService.class);

    private final UserPendingRegistrationQueryApi userPendingRegistrationQueryApi;
    private final UserRegistrationActionApi userRegistrationActionApi;
    private final RegistrationProperties properties;
    private final RegistrationCodeRepository registrationCodeStore;
    private final MailPort mailService;
    private final CaptchaApplicationService captchaService;
    private final RegistrationSessionRepository registrationSessionStore;
    private final LoginApplicationService authService;
    private final RegistrationDomainService registrationDomainService;

    public RegistrationVerificationApplicationService(
            UserPendingRegistrationQueryApi userPendingRegistrationQueryApi,
            UserRegistrationActionApi userRegistrationActionApi,
            RegistrationProperties properties,
            RegistrationCodeRepository registrationCodeStore,
            MailPort mailService,
            CaptchaApplicationService captchaService,
            RegistrationSessionRepository registrationSessionStore,
            LoginApplicationService authService,
            RegistrationDomainService registrationDomainService
    ) {
        this.userPendingRegistrationQueryApi = userPendingRegistrationQueryApi;
        this.userRegistrationActionApi = userRegistrationActionApi;
        this.properties = properties;
        this.registrationCodeStore = registrationCodeStore;
        this.mailService = mailService;
        this.captchaService = captchaService;
        this.registrationSessionStore = registrationSessionStore;
        this.authService = authService;
        this.registrationDomainService = registrationDomainService;
    }

    public RegisterCodeResendResult resendCode(ResendRegisterCodeCommand command) {
        String registrationToken = command == null ? null : command.registrationToken();
        String captchaId = command == null ? null : command.captchaId();
        String captchaCode = command == null ? null : command.captchaCode();
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
        RegistrationCodeRepository.IssueResult issueResult = registrationCodeStore.issue(user.userId(), code, ttl, cooldown);
        if (issueResult == RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_RESEND_COOLDOWN);
        }
        mailService.sendRegistrationCodeMail(user.email(), code);

        return new RegisterCodeResendResult(
                true,
                registrationDomainService.maskEmail(user.email()),
                properties.getCode().isExposeCode() ? code : null
        );
    }

    public LoginResult verifyAndLogin(VerifyRegisterCodeCommand command) {
        String registrationToken = command == null ? null : command.registrationToken();
        String code = command == null ? null : command.code();
        if (!StringUtils.hasText(registrationToken) || !StringUtils.hasText(code)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "registrationToken/code 不能为空");
        }

        UUID userId = resolveUserIdOrThrow(registrationToken);

        PendingRegistrationUserView user = requirePendingUser(userId);
        RegistrationCodeRepository.VerifyResult result = registrationCodeStore.verifyAndConsume(userId, code.trim());
        if (result == RegistrationCodeRepository.VerifyResult.SUCCESS) {
            UserCredentialView activatedUser = userRegistrationActionApi.activatePendingUser(userId);
            if (activatedUser == null || activatedUser.userId() == null) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "激活用户失败");
            }
            LoginResult loginResult = authService.issueLoginResult(activatedUser);
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
        if (result == RegistrationCodeRepository.VerifyResult.EXPIRED) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_EXPIRED);
        }
        if (result == RegistrationCodeRepository.VerifyResult.TOO_MANY_ATTEMPTS) {
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

}
