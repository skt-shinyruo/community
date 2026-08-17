package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.RegistrationVerificationApplicationService.RegisterCodeResendResult;
import com.nowcoder.community.auth.application.RegistrationVerificationApplicationService.ResendRegisterCodeCommand;
import com.nowcoder.community.auth.application.RegistrationVerificationApplicationService.VerifyRegisterCodeCommand;
import com.nowcoder.community.auth.application.port.RegistrationCodeMailDispatcher;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import com.nowcoder.community.auth.domain.service.AuthSecretGenerator;
import com.nowcoder.community.auth.domain.service.RegistrationDomainService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RegistrationVerificationApplicationServiceTest {

    private static final String ENCODED_PASSWORD = "$2a$10$7EqJtq98hPqEX7fNZaFWoOHiE9VYh4Vh7H1w52x1x7YjQwlhbR1XK";

    @Mock
    private UserRegistrationActionApi userRegistrationActionApi;

    @Mock
    private RegistrationCodeRepository registrationCodeStore;

    @Mock
    private RegistrationCodeRepository.ReplacementLease replacementLease;

    @Mock
    private RegistrationCodeRepository.VerificationClaim verificationClaim;

    @Mock
    private RegistrationCodeMailDispatcher mailDispatcher;

    @Mock
    private RegistrationRequestRateLimiter registrationRequestRateLimiter;

    @Mock
    private CaptchaChallengeComponent captchaChallenge;

    @Mock
    private LoginTokenIssuer loginTokenIssuer;

    @Mock
    private RegistrationDraftRepository registrationDraftRepository;

    private RegistrationProperties properties;
    private RegistrationVerificationApplicationService service;

    @BeforeEach
    void setUp() {
        properties = new RegistrationProperties();
        properties.getCode().setExposeCode(true);
        properties.getCode().setTtlSeconds(600);
        service = new RegistrationVerificationApplicationService(
                userRegistrationActionApi,
                properties,
                registrationCodeStore,
                mailDispatcher,
                captchaChallenge,
                registrationDraftRepository,
                loginTokenIssuer,
                new AuthSecretGenerator(),
                new RegistrationDomainService(),
                registrationRequestRateLimiter,
                Clock.systemUTC()
        );
    }

    @Test
    void resendCodeShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.resendCode(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void verifyAndLoginShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.verifyAndLogin(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void activatedRegistrationTokenShouldRequireNormalLoginForVerifyAndResend() {
        UUID userId = uuid(70);
        when(registrationDraftRepository.find("token")).thenReturn(Optional.empty());
        when(registrationDraftRepository.findActivatedUserId("token")).thenReturn(Optional.of(userId));
        assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("token", "222222")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED);
        assertThatThrownBy(() -> service.resendCode(
                new ResendRegisterCodeCommand("token", "cid", "abcd", "127.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED);

        verifyNoInteractions(userRegistrationActionApi, registrationCodeStore, mailDispatcher, loginTokenIssuer);
    }

    @Test
    void resendCodeShouldRequireCaptchaAndReturnIssuedResponse() {
        UUID userId = uuid(7);

        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.tryBeginReplacement(
                eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)),
                eq(Duration.ofSeconds(120))))
                .thenReturn(Optional.of(replacementLease));
        when(replacementLease.id()).thenReturn(uuid(700));
        RegisterCodeResendResult response = service.resendCode(
                new ResendRegisterCodeCommand("token", "cid", "abcd", "127.0.0.1"));

        assertThat(response.issued()).isTrue();
        assertThat(response.maskedEmail()).isNotBlank().contains("@").isNotEqualTo("alice@example.com");
        assertThat(response.debugEmailCode()).matches("\\d{6}");
        verify(registrationCodeStore).tryBeginReplacement(
                eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)),
                eq(Duration.ofSeconds(120)));
        verify(registrationRequestRateLimiter).enforceResend(userId, "alice@example.com", "127.0.0.1");
        ArgumentCaptor<RegistrationCodeMailDispatcher.Delivery> deliveryCaptor =
                ArgumentCaptor.forClass(RegistrationCodeMailDispatcher.Delivery.class);
        verify(mailDispatcher).dispatch(deliveryCaptor.capture());
        assertThat(deliveryCaptor.getValue().deliveryId()).isEqualTo(replacementLease.id());
        assertThat(deliveryCaptor.getValue().replacementLeaseId()).isEqualTo(replacementLease.id());
        assertThat(deliveryCaptor.getValue().registrationId()).isEqualTo(userId);
        assertThat(deliveryCaptor.getValue().code()).matches("\\d{6}");
        verify(replacementLease, never()).abort();
        verify(userRegistrationActionApi, never()).createVerifiedRegistrationUser(any());
    }

    @Test
    void resendCodeShouldClampCodeTtlToRegistrationDraftLifetime() {
        UUID userId = uuid(7);
        PreparedRegistrationDraft shortLivedDraft = new PreparedRegistrationDraft(
                userId,
                "alice",
                "alice@example.com",
                ENCODED_PASSWORD,
                "h",
                Instant.now(),
                Instant.now().plusSeconds(240)
        );

        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(shortLivedDraft));
        when(registrationCodeStore.tryBeginReplacement(
                eq(userId), matches("\\d{6}"), any(Duration.class), eq(Duration.ofSeconds(60)),
                eq(Duration.ofSeconds(120))))
                .thenReturn(Optional.of(replacementLease));
        when(replacementLease.id()).thenReturn(uuid(701));
        service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd", null));

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(registrationCodeStore).tryBeginReplacement(
                eq(userId), matches("\\d{6}"), ttlCaptor.capture(), eq(Duration.ofSeconds(60)),
                eq(Duration.ofSeconds(120)));
        assertThat(ttlCaptor.getValue())
                .isGreaterThan(Duration.ZERO)
                .isLessThanOrEqualTo(Duration.ofSeconds(240));
    }

    @Test
    void resendCodeShouldRejectDraftThatCannotCoverTheDeliveryValidityMargin() {
        UUID userId = uuid(72);
        PreparedRegistrationDraft expiringDraft = new PreparedRegistrationDraft(
                userId,
                "alice",
                "alice@example.com",
                ENCODED_PASSWORD,
                "h",
                Instant.now(),
                Instant.now().plusSeconds(20)
        );
        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(expiringDraft));

        assertThatThrownBy(() -> service.resendCode(
                new ResendRegisterCodeCommand("token", "cid", "abcd", "127.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);

        verify(registrationCodeStore, never()).tryBeginReplacement(any(), any(), any(), any(), any());
        verifyNoInteractions(mailDispatcher);
    }

    @Test
    void resendCodeShouldRejectWhenCooldownWindowIsStillActive() {
        UUID userId = uuid(7);

        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.tryBeginReplacement(
                eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)),
                eq(Duration.ofSeconds(120))))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CODE_RESEND_COOLDOWN);

        verifyNoInteractions(mailDispatcher);
        verify(userRegistrationActionApi, never()).createVerifiedRegistrationUser(any());
    }

    @Test
    void resendCodeShouldAbortReplacementWhenOutboxDispatchFails() {
        UUID userId = uuid(7);
        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.tryBeginReplacement(
                eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)),
                eq(Duration.ofSeconds(120))))
                .thenReturn(Optional.of(replacementLease));
        when(replacementLease.id()).thenReturn(uuid(702));
        doThrow(new IllegalStateException("mail down"))
                .when(mailDispatcher).dispatch(any(RegistrationCodeMailDispatcher.Delivery.class));

        assertThatThrownBy(() -> service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mail down");

        verify(replacementLease).abort();
    }

    @Test
    void resendCodeShouldStopBeforeRedisMutationWhenQuotaRejects() {
        UUID userId = uuid(7);
        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        doThrow(new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS))
                .when(registrationRequestRateLimiter).enforceResend(userId, "alice@example.com", "127.0.0.1");

        assertThatThrownBy(() -> service.resendCode(
                new ResendRegisterCodeCommand("token", "cid", "abcd", "127.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);

        verifyNoInteractions(registrationCodeStore, mailDispatcher);
    }

    @Test
    void verifyAndLoginShouldCreateVerifiedUserAndReturnLoginResult(CapturedOutput output) {
        UUID userId = uuid(7);
        UserCredentialView activatedUser = new UserCredentialView(userId, "alice", 1, 0, null, 0L, true, true);

        RefreshCookieSpec cookie = issuedCookie("rt");

        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.claimVerification(
                userId, "222222", Duration.ofSeconds(120)))
                .thenReturn(verificationClaim);
        when(userRegistrationActionApi.createVerifiedRegistrationUser(any(VerifiedRegistrationUserCommand.class)))
                .thenReturn(new UserRegistrationActionApi.VerifiedRegistrationResult(activatedUser, true));
        when(registrationDraftRepository.markActivated(
                eq("token"), eq(userId), any(Duration.class))).thenReturn(true);
        when(verificationClaim.consume()).thenReturn(true);
        when(loginTokenIssuer.issueLoginResult(activatedUser)).thenReturn(new LoginResult("access-token", cookie));

        LoginResult result = service.verifyAndLogin(new VerifyRegisterCodeCommand("token", "222222"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshCookie()).isEqualTo(cookie);
        ArgumentCaptor<VerifiedRegistrationUserCommand> commandCaptor =
                ArgumentCaptor.forClass(VerifiedRegistrationUserCommand.class);
        verify(userRegistrationActionApi).createVerifiedRegistrationUser(commandCaptor.capture());
        VerifiedRegistrationUserCommand createCommand = commandCaptor.getValue();
        assertThat(createCommand.userId()).isEqualTo(userId);
        assertThat(createCommand.username()).isEqualTo("alice");
        assertThat(createCommand.email()).isEqualTo("alice@example.com");
        assertThat(createCommand.encodedPassword()).isEqualTo(ENCODED_PASSWORD);
        assertThat(createCommand.headerUrl()).isEqualTo("h");
        verify(registrationDraftRepository).markActivated(eq("token"), eq(userId), any(Duration.class));
        verify(registrationDraftRepository, never()).delete("token");
        verify(registrationCodeStore).claimVerification(userId, "222222", Duration.ofSeconds(120));
        InOrder credentialIssuanceOrder = inOrder(verificationClaim, loginTokenIssuer);
        credentialIssuanceOrder.verify(verificationClaim).consume();
        credentialIssuanceOrder.verify(loginTokenIssuer).issueLoginResult(activatedUser);
        assertThat(output.getAll())
                .contains("user.id=" + userId)
                .contains("username=alice")
                .doesNotContain("token")
                .doesNotContain("222222");
    }

    @Test
    void verifiedUserReplayShouldRetainTerminalDraftAndNeverIssueAnotherLogin() {
        UUID userId = uuid(71);
        PreparedRegistrationDraft draft = draft(userId);
        UserCredentialView existingUser = new UserCredentialView(
                userId, "alice", 1, 0, null, 41L, true, true);
        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft));
        when(registrationCodeStore.claimVerification(
                userId, "222222", Duration.ofSeconds(120)))
                .thenReturn(verificationClaim);
        when(userRegistrationActionApi.createVerifiedRegistrationUser(any(VerifiedRegistrationUserCommand.class)))
                .thenReturn(new UserRegistrationActionApi.VerifiedRegistrationResult(existingUser, false));
        when(registrationDraftRepository.markActivated(
                eq("token"), eq(userId), any(Duration.class))).thenReturn(true);
        when(verificationClaim.consume()).thenReturn(true);

        assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("token", "222222")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED);

        verify(registrationDraftRepository).markActivated(eq("token"), eq(userId), any(Duration.class));
        verify(registrationDraftRepository, never()).delete("token");
        verify(loginTokenIssuer, never()).issueLoginResult(any(UserCredentialView.class));
    }

    @Test
    void verifyAndLoginShouldRetainTerminalDraftWhenLoginIssuanceFailsAfterUserCreation() {
        UUID userId = uuid(7);
        UserCredentialView activatedUser = new UserCredentialView(userId, "alice", 1, 0, null, 0L, true, true);

        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.claimVerification(
                userId, "222222", Duration.ofSeconds(120)))
                .thenReturn(verificationClaim);
        when(userRegistrationActionApi.createVerifiedRegistrationUser(any(VerifiedRegistrationUserCommand.class)))
                .thenReturn(new UserRegistrationActionApi.VerifiedRegistrationResult(activatedUser, true));
        when(registrationDraftRepository.markActivated(
                eq("token"), eq(userId), any(Duration.class))).thenReturn(true);
        when(verificationClaim.consume()).thenReturn(true);
        when(loginTokenIssuer.issueLoginResult(activatedUser)).thenThrow(new IllegalStateException("token down"));

        assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("token", "222222")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED);

        verify(registrationDraftRepository).markActivated(eq("token"), eq(userId), any(Duration.class));
        verify(registrationDraftRepository, never()).delete("token");
        verify(verificationClaim).consume();
    }

    @Test
    void verifyAndLoginShouldNotIssueCredentialsWhenVerificationLeaseCanNoLongerConsume() {
        UUID userId = uuid(7);
        UserCredentialView activatedUser = new UserCredentialView(userId, "alice", 1, 0, null, 0L, true, true);

        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.claimVerification(
                userId, "222222", Duration.ofSeconds(120)))
                .thenReturn(verificationClaim);
        when(userRegistrationActionApi.createVerifiedRegistrationUser(any(VerifiedRegistrationUserCommand.class)))
                .thenReturn(new UserRegistrationActionApi.VerifiedRegistrationResult(activatedUser, true));
        when(registrationDraftRepository.markActivated(
                eq("token"), eq(userId), any(Duration.class))).thenReturn(true);
        when(verificationClaim.consume()).thenReturn(false);

        assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("token", "222222")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED);

        verify(loginTokenIssuer, never()).issueLoginResult(any(UserCredentialView.class));
        verify(registrationDraftRepository, never()).delete("token");
    }

    @ParameterizedTest
    @CsvSource({
            "false,true",
            "true,false"
    })
    void verifyAndLoginShouldNotIssueCredentialsWhenActivatedUserCannotLoginOrRefresh(
            boolean loginAllowed,
            boolean refreshAllowed
    ) {
        UUID userId = uuid(7);
        UserCredentialView activatedUser = new UserCredentialView(
                userId, "alice", 1, 0, null, 0L, loginAllowed, refreshAllowed);

        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.claimVerification(
                userId, "222222", Duration.ofSeconds(120)))
                .thenReturn(verificationClaim);
        when(userRegistrationActionApi.createVerifiedRegistrationUser(any(VerifiedRegistrationUserCommand.class)))
                .thenReturn(new UserRegistrationActionApi.VerifiedRegistrationResult(activatedUser, true));
        when(registrationDraftRepository.markActivated(
                eq("token"), eq(userId), any(Duration.class))).thenReturn(true);
        when(verificationClaim.consume()).thenReturn(true);

        assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("token", "222222")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.USER_DISABLED);

        verify(verificationClaim).consume();
        verify(registrationDraftRepository).markActivated(eq("token"), eq(userId), any(Duration.class));
        verify(registrationDraftRepository, never()).delete("token");
        verify(loginTokenIssuer, never()).issueLoginResult(any(UserCredentialView.class));
    }

    @Test
    void verifyAndLoginShouldRestorePendingCodeWhenUserCreationFailsBeforeActivation() {
        UUID userId = uuid(7);
        PreparedRegistrationDraft draft = draft(userId);
        when(registrationDraftRepository.find("reg-token")).thenReturn(Optional.of(draft));
        when(registrationCodeStore.claimVerification(
                draft.userId(), "123456", Duration.ofSeconds(120)))
                .thenReturn(verificationClaim);
        RuntimeException createFailure = new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "用户名或邮箱已存在");
        when(userRegistrationActionApi.createVerifiedRegistrationUser(any()))
                .thenThrow(createFailure);

        assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("reg-token", "123456")))
                .isSameAs(createFailure);

        verify(verificationClaim).restore();
        verify(verificationClaim, never()).consume();
        verify(registrationDraftRepository, never()).delete("reg-token");
    }

    @Test
    void verifyAndLoginShouldRejectInvalidCodeWithoutIssuingLogin() {
        UUID userId = uuid(7);

        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.claimVerification(
                userId, "111111", Duration.ofSeconds(120)))
                .thenReturn(RegistrationCodeRepository.VerificationFailure.MISMATCH);

        assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("token", "111111")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CODE_INVALID);

        verify(loginTokenIssuer, never()).issueLoginResult(any(UserCredentialView.class));
        verify(userRegistrationActionApi, never()).createVerifiedRegistrationUser(any());
        verify(registrationDraftRepository, never()).delete(any());
    }

    @Test
    void resendCodeShouldRejectWhenRegistrationTokenIsMissingOrExpired() {
        when(registrationDraftRepository.find("token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);

        verifyNoInteractions(userRegistrationActionApi, registrationCodeStore, mailDispatcher);
    }

    @Test
    void resendCodeShouldRejectAndDeleteMalformedDraftBeforeIssuingCode() {
        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(new PreparedRegistrationDraft(
                uuid(7),
                "alice",
                "",
                ENCODED_PASSWORD,
                "h",
                Instant.parse("2026-05-03T01:00:00Z"),
                Instant.now().plus(Duration.ofMinutes(30))
        )));

        assertThatThrownBy(() -> service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);

        verify(registrationDraftRepository).delete("token");
        verifyNoInteractions(userRegistrationActionApi, registrationCodeStore, mailDispatcher);
    }

    @Test
    void verifyAndLoginShouldRejectAndDeleteExpiredDraftBeforeConsumingCode() {
        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(new PreparedRegistrationDraft(
                uuid(7),
                "alice",
                "alice@example.com",
                ENCODED_PASSWORD,
                "h",
                Instant.parse("2026-05-03T01:00:00Z"),
                Instant.now().minus(Duration.ofSeconds(1))
        )));

        assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("token", "222222")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);

        verify(registrationDraftRepository).delete("token");
        verifyNoInteractions(userRegistrationActionApi, registrationCodeStore, mailDispatcher, loginTokenIssuer);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static RefreshCookieSpec issuedCookie(String value) {
        return new RefreshCookieSpec(
                "refresh_token",
                value,
                true,
                false,
                "/api/auth",
                "Lax",
                600
        );
    }

    private static PreparedRegistrationDraft draft(UUID userId) {
        return new PreparedRegistrationDraft(
                userId,
                "alice",
                "alice@example.com",
                ENCODED_PASSWORD,
                "h",
                Instant.parse("2026-05-03T01:00:00Z"),
                Instant.now().plus(Duration.ofMinutes(30))
        );
    }
}
