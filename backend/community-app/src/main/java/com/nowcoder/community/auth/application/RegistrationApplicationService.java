package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.RegisterCommand;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.result.RegisterResult;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import com.nowcoder.community.auth.domain.service.RegistrationDomainService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.auth.logging.SecurityEventLogger;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PreparedRegistrationUserView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
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
    private final RegistrationDraftRepository registrationDraftRepository;
    private final RegistrationDomainService registrationDomainService;

    public RegistrationApplicationService(
            UserRegistrationActionApi userRegistrationActionApi,
            RegistrationProperties properties,
            MailPort mailService,
            CaptchaApplicationService captchaService,
            RegistrationCodeRepository registrationCodeStore,
            RegistrationDraftRepository registrationDraftRepository,
            RegistrationDomainService registrationDomainService
    ) {
        this.userRegistrationActionApi = userRegistrationActionApi;
        this.properties = properties;
        this.mailService = mailService;
        this.captchaService = captchaService;
        this.registrationCodeStore = registrationCodeStore;
        this.registrationDraftRepository = registrationDraftRepository;
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

        Duration registrationDraftTtl = Duration.ofSeconds(Math.max(60, properties.getDraft().getTtlSeconds()));
        PreparedRegistrationUserView prepared = userRegistrationActionApi.prepareRegistrationUser(username, password, email);
        if (prepared == null
                || prepared.userId() == null
                || !StringUtils.hasText(prepared.username())
                || !StringUtils.hasText(prepared.email())
                || !StringUtils.hasText(prepared.encodedPassword())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册上下文创建失败");
        }
        String targetEmail = prepared.email();

        String code = generateCode();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getCode().getTtlSeconds()));
        Duration cooldown = Duration.ofSeconds(Math.max(0, properties.getCode().getResendCooldownSeconds()));
        String registrationToken = null;
        try {
            Instant issuedAt = Instant.now();
            registrationToken = registrationDraftRepository == null ? null : registrationDraftRepository.issue(
                    new PreparedRegistrationDraft(
                            prepared.userId(),
                            prepared.username(),
                            prepared.email(),
                            prepared.encodedPassword(),
                            prepared.headerUrl(),
                            issuedAt,
                            issuedAt.plus(registrationDraftTtl)
                    ),
                    registrationDraftTtl
            );
            if (!StringUtils.hasText(registrationToken)) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册上下文创建失败");
            }

            RegistrationCodeRepository.IssueResult issueResult = registrationCodeStore.issue(prepared.userId(), code, ttl, cooldown);
            if (issueResult != RegistrationCodeRepository.IssueResult.ISSUED) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册验证码签发失败");
            }

            mailService.sendRegistrationCodeMail(targetEmail, code);
        } catch (RuntimeException ex) {
            rollbackFailedRegistration(prepared.userId(), registrationToken);
            throw ex;
        }

        SecurityEventLogger.info(log, "registration_code_issue", "success",
                "user.id", prepared.userId(),
                "username", username,
                "masked.email", registrationDomainService.maskEmail(targetEmail));
        return new RegisterResult(
                prepared.userId(),
                registrationToken,
                true,
                registrationDomainService.maskEmail(targetEmail),
                properties.getCode().isExposeCode() ? code : null
        );
    }

    private String generateCode() {
        return Integer.toString(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    private void rollbackFailedRegistration(UUID userId, String registrationToken) {
        if (StringUtils.hasText(registrationToken) && registrationDraftRepository != null) {
            try {
                registrationDraftRepository.delete(registrationToken);
            } catch (RuntimeException cleanupEx) {
                log.warn("[registration] failed to cleanup draft for userId={}: {}", userId, cleanupEx.toString());
            }
        }

        if (userId != null && registrationCodeStore != null) {
            try {
                registrationCodeStore.delete(userId);
            } catch (RuntimeException cleanupEx) {
                log.warn("[registration] failed to cleanup code for userId={}: {}", userId, cleanupEx.toString());
            }
        }
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
