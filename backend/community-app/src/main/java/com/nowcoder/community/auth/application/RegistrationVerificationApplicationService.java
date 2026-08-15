package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.RegistrationCodeMailDispatcher;
import com.nowcoder.community.auth.application.result.LoginResult;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class RegistrationVerificationApplicationService {

    public record ResendRegisterCodeCommand(
            String registrationToken,
            String captchaId,
            String captchaCode,
            String clientIp
    ) {
    }

    public record VerifyRegisterCodeCommand(String registrationToken, String code) {
    }

    public record RegisterCodeResendResult(
            boolean issued,
            String maskedEmail,
            String debugEmailCode
    ) {
    }

    private static final Logger log = LoggerFactory.getLogger(RegistrationVerificationApplicationService.class);
    private final UserRegistrationActionApi userRegistrationActionApi;
    private final RegistrationProperties properties;
    private final RegistrationCodeRepository registrationCodeStore;
    private final RegistrationCodeMailDispatcher mailDispatcher;
    private final CaptchaChallengeComponent captchaChallenge;
    private final RegistrationDraftRepository registrationDraftRepository;
    private final LoginTokenIssuer loginTokenIssuer;
    private final AuthSecretGenerator authSecretGenerator;
    private final RegistrationDomainService registrationDomainService;
    private final RegistrationRequestRateLimiter registrationRequestRateLimiter;
    private final Clock clock;

    public RegistrationVerificationApplicationService(
            UserRegistrationActionApi userRegistrationActionApi,
            RegistrationProperties properties,
            RegistrationCodeRepository registrationCodeStore,
            RegistrationCodeMailDispatcher mailDispatcher,
            CaptchaChallengeComponent captchaChallenge,
            RegistrationDraftRepository registrationDraftRepository,
            LoginTokenIssuer loginTokenIssuer,
            AuthSecretGenerator authSecretGenerator,
            RegistrationDomainService registrationDomainService,
            RegistrationRequestRateLimiter registrationRequestRateLimiter,
            Clock clock
    ) {
        this.userRegistrationActionApi = Objects.requireNonNull(
                userRegistrationActionApi, "userRegistrationActionApi must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.registrationCodeStore = Objects.requireNonNull(
                registrationCodeStore, "registrationCodeStore must not be null");
        this.mailDispatcher = Objects.requireNonNull(mailDispatcher, "mailDispatcher must not be null");
        this.captchaChallenge = Objects.requireNonNull(captchaChallenge, "captchaChallenge must not be null");
        this.registrationDraftRepository = Objects.requireNonNull(
                registrationDraftRepository, "registrationDraftRepository must not be null");
        this.loginTokenIssuer = Objects.requireNonNull(loginTokenIssuer, "loginTokenIssuer must not be null");
        this.authSecretGenerator = Objects.requireNonNull(
                authSecretGenerator, "authSecretGenerator must not be null");
        this.registrationDomainService = Objects.requireNonNull(
                registrationDomainService, "registrationDomainService must not be null");
        this.registrationRequestRateLimiter = Objects.requireNonNull(
                registrationRequestRateLimiter, "registrationRequestRateLimiter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public RegisterCodeResendResult resendCode(ResendRegisterCodeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String registrationToken = command.registrationToken();
        String captchaId = command.captchaId();
        String captchaCode = command.captchaCode();
        captchaChallenge.requireValidCaptcha(captchaId, captchaCode);

        PreparedRegistrationDraft draft = resolveDraftOrThrow(registrationToken);
        registrationRequestRateLimiter.enforceResend(
                draft.userId(), draft.email(), command.clientIp());

        String code = generateCode();
        Duration ttl = codeTtlWithinDraftLifetime(registrationToken, draft);
        Duration cooldown = Duration.ofSeconds(Math.max(0, properties.getCode().getResendCooldownSeconds()));
        Instant issuedAt = clock.instant();
        RegistrationCodeRepository.ReplacementLease replacementLease = registrationCodeStore.tryBeginReplacement(
                        draft.userId(), code, ttl, cooldown, operationLeaseTtl())
                .orElse(null);
        if (replacementLease == null) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_RESEND_COOLDOWN);
        }
        try {
            mailDispatcher.dispatch(new RegistrationCodeMailDispatcher.Delivery(
                    replacementLease.id(),
                    draft.userId(),
                    replacementLease.id(),
                    draft.email(),
                    code,
                    issuedAt.plus(ttl)
            ));
        } catch (RuntimeException ex) {
            replacementLease.abort();
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

        RegistrationCodeRepository.VerificationResult result = registrationCodeStore.claimVerification(
                draft.userId(), code.trim(), operationLeaseTtl());
        if (result instanceof RegistrationCodeRepository.VerificationClaim verificationClaim) {
            UserRegistrationActionApi.VerifiedRegistrationResult activation;
            try {
                activation = userRegistrationActionApi.createVerifiedRegistrationUser(
                        new VerifiedRegistrationUserCommand(
                                draft.userId(),
                                draft.username(),
                                draft.email(),
                                draft.encodedPassword(),
                                draft.headerUrl()
                        )
                );
                if (activation == null || activation.user() == null || activation.user().userId() == null) {
                    throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
                }
            } catch (RuntimeException ex) {
                verificationClaim.restore();
                throw ex;
            }

            UserCredentialView activatedUser = activation.user();
            retainActivatedDraftOrThrow(registrationToken, draft);

            boolean consumed;
            try {
                consumed = verificationClaim.consume();
            } catch (RuntimeException ex) {
                throw new BusinessException(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED, ex);
            }
            if (!consumed) {
                throw new BusinessException(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED);
            }

            if (!activation.created()) {
                throw new BusinessException(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED);
            }
            if (!activatedUser.loginAllowed() || !activatedUser.refreshAllowed()) {
                throw new BusinessException(AuthErrorCode.USER_DISABLED);
            }

            try {
                LoginResult loginResult = loginTokenIssuer.issueLoginResult(activatedUser);
                SecurityEventLogger.info(log, "registration_verify", "success",
                        "user.id", activatedUser.userId(),
                        "username", activatedUser.username());
                return loginResult;
            } catch (RuntimeException ex) {
                throw new BusinessException(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED, ex);
            }
        }
        if (result == RegistrationCodeRepository.VerificationFailure.EXPIRED) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_EXPIRED);
        }
        if (result == RegistrationCodeRepository.VerificationFailure.TOO_MANY_ATTEMPTS) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CODE_TOO_MANY_ATTEMPTS);
        }
        if (result == RegistrationCodeRepository.VerificationFailure.PENDING_CONFLICT) {
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
                .orElse(null);
        if (draft == null) {
            if (registrationDraftRepository.findActivatedUserId(token).isPresent()) {
                throw new BusinessException(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED);
            }
            throw new BusinessException(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);
        }
        if (!isUsableDraft(draft)) {
            deleteDraftQuietly(token);
            throw new BusinessException(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);
        }
        return draft;
    }

    private void retainActivatedDraftOrThrow(
            String registrationToken,
            PreparedRegistrationDraft draft
    ) {
        Duration remaining = Duration.between(clock.instant(), draft.expiresAt());
        if (remaining.isNegative() || remaining.isZero() || remaining.toMillis() <= 0) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED);
        }
        try {
            if (!registrationDraftRepository.markActivated(
                    registrationToken.trim(), draft.userId(), remaining)) {
                throw new BusinessException(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED, exception);
        }
    }

    private boolean isUsableDraft(PreparedRegistrationDraft draft) {
        return draft != null
                && draft.userId() != null
                && StringUtils.hasText(draft.username())
                && StringUtils.hasText(draft.email())
                && StringUtils.hasText(draft.encodedPassword())
                && draft.expiresAt() != null
                && clock.instant().isBefore(draft.expiresAt());
    }

    private Duration codeTtlWithinDraftLifetime(String registrationToken, PreparedRegistrationDraft draft) {
        Duration configuredTtl = Duration.ofSeconds(Math.max(60, properties.getCode().getTtlSeconds()));
        Duration draftRemainingTtl = Duration.between(clock.instant(), draft.expiresAt());
        if (draftRemainingTtl.isNegative() || draftRemainingTtl.isZero()
                || draftRemainingTtl.compareTo(operationLeaseTtl()) <= 0) {
            deleteDraftQuietly(registrationToken);
            throw new BusinessException(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);
        }
        return configuredTtl.compareTo(draftRemainingTtl) <= 0 ? configuredTtl : draftRemainingTtl;
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

    private String generateCode() {
        return authSecretGenerator.numericCode(6);
    }

    private Duration operationLeaseTtl() {
        return Duration.ofSeconds(Math.max(60, properties.getCode().getOperationLeaseSeconds()));
    }

}
