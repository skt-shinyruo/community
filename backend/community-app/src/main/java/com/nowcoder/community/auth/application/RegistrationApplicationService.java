package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.RegistrationCodeMailDispatcher;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import com.nowcoder.community.auth.domain.service.AuthSecretGenerator;
import com.nowcoder.community.auth.domain.service.RegistrationDomainService;
import com.nowcoder.community.common.logging.SecurityEventLogger;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PreparedRegistrationUserView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class RegistrationApplicationService {

    public record RegisterCommand(
            String username,
            String password,
            String email,
            String captchaId,
            String captchaCode,
            String clientIp
    ) {
    }

    public record RegisterResult(
            UUID userId,
            String registrationToken,
            boolean emailCodeIssued,
            String maskedEmail,
            String debugEmailCode
    ) {
    }

    private static final Logger log = LoggerFactory.getLogger(RegistrationApplicationService.class);

    private final UserRegistrationActionApi userRegistrationActionApi;
    private final RegistrationProperties properties;
    private final RegistrationCodeMailDispatcher mailDispatcher;
    private final CaptchaChallengeComponent captchaChallenge;
    private final RegistrationCodeRepository registrationCodeStore;
    private final RegistrationDraftRepository registrationDraftRepository;
    private final AuthSecretGenerator authSecretGenerator;
    private final RegistrationDomainService registrationDomainService;
    private final RegistrationRequestRateLimiter registrationRequestRateLimiter;
    private final Clock clock;

    @Autowired
    public RegistrationApplicationService(
            UserRegistrationActionApi userRegistrationActionApi,
            RegistrationProperties properties,
            RegistrationCodeMailDispatcher mailDispatcher,
            CaptchaChallengeComponent captchaChallenge,
            RegistrationCodeRepository registrationCodeStore,
            RegistrationDraftRepository registrationDraftRepository,
            AuthSecretGenerator authSecretGenerator,
            RegistrationDomainService registrationDomainService,
            RegistrationRequestRateLimiter registrationRequestRateLimiter,
            Clock clock
    ) {
        this.userRegistrationActionApi = Objects.requireNonNull(
                userRegistrationActionApi, "userRegistrationActionApi must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.mailDispatcher = Objects.requireNonNull(mailDispatcher, "mailDispatcher must not be null");
        this.captchaChallenge = Objects.requireNonNull(captchaChallenge, "captchaChallenge must not be null");
        this.registrationCodeStore = Objects.requireNonNull(
                registrationCodeStore, "registrationCodeStore must not be null");
        this.registrationDraftRepository = Objects.requireNonNull(
                registrationDraftRepository, "registrationDraftRepository must not be null");
        this.authSecretGenerator = Objects.requireNonNull(
                authSecretGenerator, "authSecretGenerator must not be null");
        this.registrationDomainService = Objects.requireNonNull(
                registrationDomainService, "registrationDomainService must not be null");
        this.registrationRequestRateLimiter = Objects.requireNonNull(
                registrationRequestRateLimiter, "registrationRequestRateLimiter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public RegisterResult register(RegisterCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        captchaChallenge.requireValidCaptcha(command.captchaId(), command.captchaCode());

        String username = safeTrim(command.username());
        String password = command.password() == null ? "" : command.password();
        String email = safeTrim(command.email());

        registrationDomainService.requireRegisterFields(username, password, email);
        registrationRequestRateLimiter.enforce(username, email, command.clientIp());

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
        UUID deliveryId = UUID.randomUUID();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getCode().getTtlSeconds()));
        Duration cooldown = Duration.ofSeconds(Math.max(0, properties.getCode().getResendCooldownSeconds()));
        String registrationToken = null;
        try {
            Instant issuedAt = clock.instant();
            PreparedRegistrationDraft draft = new PreparedRegistrationDraft(
                    prepared.userId(),
                    prepared.username(),
                    prepared.email(),
                    prepared.encodedPassword(),
                    prepared.headerUrl(),
                    issuedAt,
                    issuedAt.plus(registrationDraftTtl)
            );
            registrationToken = storeRegistrationDraft(draft, registrationDraftTtl);
            if (!StringUtils.hasText(registrationToken)) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册上下文创建失败");
            }

            RegistrationCodeRepository.IssueResult issueResult = registrationCodeStore.issue(
                    prepared.userId(), code, ttl, cooldown, deliveryId);
            if (issueResult != RegistrationCodeRepository.IssueResult.ISSUED) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册验证码签发失败");
            }

            mailDispatcher.dispatch(new RegistrationCodeMailDispatcher.Delivery(
                    deliveryId,
                    prepared.userId(),
                    null,
                    targetEmail,
                    code,
                    issuedAt.plus(ttl)
            ));
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
        return authSecretGenerator.numericCode(6);
    }

    private String storeRegistrationDraft(PreparedRegistrationDraft draft, Duration ttl) {
        if (registrationDraftRepository == null) {
            return null;
        }
        for (int i = 0; i < 5; i++) {
            String token = authSecretGenerator.opaqueToken();
            if (registrationDraftRepository.store(token, draft, ttl)) {
                return token;
            }
        }
        return null;
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
