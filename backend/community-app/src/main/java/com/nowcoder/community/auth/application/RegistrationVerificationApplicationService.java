package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.ResendRegisterCodeCommand;
import com.nowcoder.community.auth.application.command.VerifyRegisterCodeCommand;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.RegisterCodeResendResult;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import com.nowcoder.community.auth.domain.service.AuthSecretGenerator;
import com.nowcoder.community.auth.domain.service.RegistrationDomainService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.auth.logging.SecurityEventLogger;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class RegistrationVerificationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationVerificationApplicationService.class);

    private final UserRegistrationActionApi userRegistrationActionApi;
    private final RegistrationProperties properties;
    private final RegistrationCodeRepository registrationCodeStore;
    private final MailPort mailService;
    private final CaptchaChallengeComponent captchaChallenge;
    private final RegistrationDraftRepository registrationDraftRepository;
    private final LoginTokenIssuer loginTokenIssuer;
    private final AuthSecretGenerator authSecretGenerator;
    private final RegistrationDomainService registrationDomainService;

    public RegistrationVerificationApplicationService(
            UserRegistrationActionApi userRegistrationActionApi,
            RegistrationProperties properties,
            RegistrationCodeRepository registrationCodeStore,
            MailPort mailService,
            CaptchaChallengeComponent captchaChallenge,
            RegistrationDraftRepository registrationDraftRepository,
            LoginTokenIssuer loginTokenIssuer,
            AuthSecretGenerator authSecretGenerator,
            RegistrationDomainService registrationDomainService
    ) {
        this.userRegistrationActionApi = userRegistrationActionApi;
        this.properties = properties;
        this.registrationCodeStore = registrationCodeStore;
        this.mailService = mailService;
        this.captchaChallenge = captchaChallenge;
        this.registrationDraftRepository = registrationDraftRepository;
        this.loginTokenIssuer = loginTokenIssuer;
        this.authSecretGenerator = authSecretGenerator;
        this.registrationDomainService = registrationDomainService;
    }

    public RegisterCodeResendResult resendCode(ResendRegisterCodeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String registrationToken = command.registrationToken();
        String captchaId = command.captchaId();
        String captchaCode = command.captchaCode();
        captchaChallenge.requireValidCaptcha(captchaId, captchaCode);

        PreparedRegistrationDraft draft = resolveDraftOrThrow(registrationToken);

        String code = generateCode();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getCode().getTtlSeconds()));
        Duration cooldown = Duration.ofSeconds(Math.max(0, properties.getCode().getResendCooldownSeconds()));
        RegistrationCodeRepository.IssueResult issueResult = registrationCodeStore.beginReplacement(draft.userId(), code, ttl, cooldown);
        if (issueResult == RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_RESEND_COOLDOWN);
        }
        try {
            mailService.sendRegistrationCodeMail(draft.email(), code);
            registrationCodeStore.promoteReplacement(draft.userId());
        } catch (RuntimeException ex) {
            registrationCodeStore.abortReplacement(draft.userId());
            throw ex;
        }

        return new RegisterCodeResendResult(
                true,
                registrationDomainService.maskEmail(draft.email()),
                properties.getCode().isExposeCode() ? code : null
        );
    }

    public LoginResult verifyAndLogin(VerifyRegisterCodeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String registrationToken = command.registrationToken();
        String code = command.code();
        if (!StringUtils.hasText(registrationToken) || !StringUtils.hasText(code)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "registrationToken/code 不能为空");
        }

        PreparedRegistrationDraft draft = resolveDraftOrThrow(registrationToken);

        Duration pendingTtl = Duration.ofSeconds(60);
        RegistrationCodeRepository.VerifyResult result = registrationCodeStore.verifyForConsumption(draft.userId(), code.trim(), pendingTtl);
        if (result == RegistrationCodeRepository.VerifyResult.PENDING) {
            boolean activated = false;
            try {
                UserCredentialView activatedUser = userRegistrationActionApi.createVerifiedRegistrationUser(
                        new VerifiedRegistrationUserCommand(
                                draft.userId(),
                                draft.username(),
                                draft.email(),
                                draft.encodedPassword(),
                                draft.headerUrl()
                        )
                );
                if (activatedUser == null || activatedUser.userId() == null) {
                    throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
                }
                activated = true;
                LoginResult loginResult = loginTokenIssuer.issueLoginResult(activatedUser);
                SecurityEventLogger.info(log, "registration_verify", "success",
                        "user.id", activatedUser.userId(),
                        "username", activatedUser.username());
                return loginResult;
            } catch (RuntimeException ex) {
                if (activated) {
                    throw new BusinessException(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED, ex);
                }
                registrationCodeStore.restorePending(draft.userId());
                throw ex;
            } finally {
                if (activated) {
                    consumePendingQuietly(draft.userId());
                    deleteDraftQuietly(registrationToken);
                }
            }
        }
        if (result == RegistrationCodeRepository.VerifyResult.EXPIRED) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_EXPIRED);
        }
        if (result == RegistrationCodeRepository.VerifyResult.TOO_MANY_ATTEMPTS) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_TOO_MANY_ATTEMPTS);
        }
        if (result == RegistrationCodeRepository.VerifyResult.PENDING_CONFLICT) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_INVALID);
        }
        throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_INVALID);
    }

    private PreparedRegistrationDraft resolveDraftOrThrow(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "registrationToken 不能为空");
        }
        if (registrationDraftRepository == null) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);
        }
        String token = registrationToken.trim();
        PreparedRegistrationDraft draft = registrationDraftRepository.find(token)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REGISTRATION_CONTEXT_INVALID));
        if (!isUsableDraft(draft)) {
            deleteDraftQuietly(token);
            throw new BusinessException(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);
        }
        return draft;
    }

    private boolean isUsableDraft(PreparedRegistrationDraft draft) {
        return draft != null
                && draft.userId() != null
                && StringUtils.hasText(draft.username())
                && StringUtils.hasText(draft.email())
                && StringUtils.hasText(draft.encodedPassword())
                && draft.expiresAt() != null
                && Instant.now().isBefore(draft.expiresAt());
    }

    private void deleteDraftQuietly(String registrationToken) {
        if (!StringUtils.hasText(registrationToken) || registrationDraftRepository == null) {
            return;
        }
        try {
            registrationDraftRepository.delete(registrationToken.trim());
        } catch (RuntimeException ignored) {
            // best-effort cleanup
        }
    }

    private void consumePendingQuietly(UUID userId) {
        if (userId == null || registrationCodeStore == null) {
            return;
        }
        try {
            registrationCodeStore.consumePending(userId);
        } catch (RuntimeException ignored) {
            // best-effort cleanup
        }
    }

    private String generateCode() {
        return authSecretGenerator.numericCode(6);
    }

}
