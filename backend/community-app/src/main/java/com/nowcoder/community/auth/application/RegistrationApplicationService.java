package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.RegisterCommand;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.result.RegisterResult;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import com.nowcoder.community.auth.domain.repository.RegistrationSessionRepository;
import com.nowcoder.community.auth.domain.service.RegistrationDomainService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.auth.logging.SecurityEventLogger;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PendingRegistrationUserView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RegistrationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationApplicationService.class);

    private final UserRegistrationActionApi userRegistrationActionApi;
    private final RegistrationProperties properties;
    private final MailPort mailService;
    private final CaptchaApplicationService captchaService;
    private final RegistrationCodeRepository registrationCodeStore;
    private final RegistrationSessionRepository registrationSessionStore;
    private final RegistrationDomainService registrationDomainService;

    public RegistrationApplicationService(
            UserRegistrationActionApi userRegistrationActionApi,
            RegistrationProperties properties,
            MailPort mailService,
            CaptchaApplicationService captchaService,
            RegistrationCodeRepository registrationCodeStore,
            RegistrationSessionRepository registrationSessionStore,
            RegistrationDomainService registrationDomainService
    ) {
        this.userRegistrationActionApi = userRegistrationActionApi;
        this.properties = properties;
        this.mailService = mailService;
        this.captchaService = captchaService;
        this.registrationCodeStore = registrationCodeStore;
        this.registrationSessionStore = registrationSessionStore;
        this.registrationDomainService = registrationDomainService;
    }

    public RegisterResult register(RegisterCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "参数不能为空");
        }
        if (!StringUtils.hasText(command.captchaId()) || !StringUtils.hasText(command.captchaCode())) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
        if (!captchaService.verify(command.captchaId(), command.captchaCode())) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
        }

        String username = safeTrim(command.username());
        String password = safeTrim(command.password());
        String email = safeTrim(command.email());

        registrationDomainService.requireRegisterFields(username, password, email);

        Duration pendingUserTtl = Duration.ofSeconds(Math.max(60, properties.getPendingUser().getTtlSeconds()));
        PendingRegistrationUserView created = userRegistrationActionApi.registerPendingUser(username, password, email, pendingUserTtl);
        if (created == null || created.userId() == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
        }
        String targetEmail = StringUtils.hasText(created.email()) ? created.email() : email;

        String code = generateCode();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getCode().getTtlSeconds()));
        Duration cooldown = Duration.ofSeconds(Math.max(0, properties.getCode().getResendCooldownSeconds()));
        String registrationToken = null;
        try {
            registrationToken = registrationSessionStore == null ? null : registrationSessionStore.issue(created.userId(), pendingUserTtl);
            if (!StringUtils.hasText(registrationToken)) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册上下文创建失败");
            }

            RegistrationCodeRepository.IssueResult issueResult = registrationCodeStore.issue(created.userId(), code, ttl, cooldown);
            if (issueResult != RegistrationCodeRepository.IssueResult.ISSUED) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册验证码签发失败");
            }

            mailService.sendRegistrationCodeMail(targetEmail, code);
        } catch (RuntimeException ex) {
            rollbackFailedRegistration(created.userId(), registrationToken);
            throw ex;
        }

        SecurityEventLogger.info(log, "registration_code_issue", "success",
                "user.id", created.userId(),
                "username", username,
                "masked.email", registrationDomainService.maskEmail(targetEmail));
        return new RegisterResult(
                created.userId(),
                registrationToken,
                true,
                registrationDomainService.maskEmail(targetEmail),
                properties.getCode().isExposeCode() ? code : null
        );
    }

    public int cleanupExpiredPendingUsers() {
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getPendingUser().getTtlSeconds()));
        int deleted;
        int totalDeleted = 0;
        do {
            deleted = userRegistrationActionApi.cleanupExpiredPendingUsers(ttl);
            totalDeleted += deleted;
        } while (deleted > 0);
        return totalDeleted;
    }

    private String generateCode() {
        return Integer.toString(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    private void rollbackFailedRegistration(UUID userId, String registrationToken) {
        if (StringUtils.hasText(registrationToken) && registrationSessionStore != null) {
            try {
                registrationSessionStore.delete(registrationToken);
            } catch (RuntimeException cleanupEx) {
                log.warn("[registration] failed to cleanup session for userId={}: {}", userId, cleanupEx.toString());
            }
        }

        if (userId != null && registrationCodeStore != null) {
            try {
                registrationCodeStore.delete(userId);
            } catch (RuntimeException cleanupEx) {
                log.warn("[registration] failed to cleanup code for userId={}: {}", userId, cleanupEx.toString());
            }
        }

        if (userId != null) {
            try {
                userRegistrationActionApi.deletePendingUser(userId);
            } catch (RuntimeException cleanupEx) {
                log.warn("[registration] failed to cleanup pending user userId={}: {}", userId, cleanupEx.toString());
            }
        }
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
