package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.ResendRegisterCodeCommand;
import com.nowcoder.community.auth.application.command.VerifyRegisterCodeCommand;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.application.result.RegisterCodeResendResult;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
    private MailPort mailService;

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
                mailService,
                captchaChallenge,
                registrationDraftRepository,
                loginTokenIssuer,
                new AuthSecretGenerator(),
                new RegistrationDomainService()
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
    void resendCodeShouldRequireCaptchaAndReturnIssuedResponse() {
        UUID userId = uuid(7);

        doNothing().when(captchaChallenge).requireValidCaptcha("cid", "abcd");
        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.beginReplacement(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60))))
                .thenReturn(RegistrationCodeRepository.IssueResult.ISSUED);

        RegisterCodeResendResult response = service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd"));

        assertThat(response.issued()).isTrue();
        assertThat(response.maskedEmail()).isNotBlank().contains("@").isNotEqualTo("alice@example.com");
        assertThat(response.debugEmailCode()).matches("\\d{6}");
        verify(registrationCodeStore).beginReplacement(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)));
        verify(registrationCodeStore).promoteReplacement(userId);
        verify(mailService).sendRegistrationCodeMail(eq("alice@example.com"), matches("\\d{6}"));
        verify(userRegistrationActionApi, never()).createVerifiedRegistrationUser(any());
    }

    @Test
    void resendCodeShouldRejectWhenCooldownWindowIsStillActive() {
        UUID userId = uuid(7);

        doNothing().when(captchaChallenge).requireValidCaptcha("cid", "abcd");
        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.beginReplacement(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60))))
                .thenReturn(RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE);

        assertThatThrownBy(() -> service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CODE_RESEND_COOLDOWN);

        verifyNoInteractions(mailService);
        verify(userRegistrationActionApi, never()).createVerifiedRegistrationUser(any());
    }

    @Test
    void resendCodeShouldAbortReplacementWhenMailSendingFails() {
        UUID userId = uuid(7);
        doNothing().when(captchaChallenge).requireValidCaptcha("cid", "abcd");
        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.beginReplacement(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60))))
                .thenReturn(RegistrationCodeRepository.IssueResult.ISSUED);
        doThrow(new IllegalStateException("mail down"))
                .when(mailService).sendRegistrationCodeMail(eq("alice@example.com"), matches("\\d{6}"));

        assertThatThrownBy(() -> service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mail down");

        verify(registrationCodeStore).abortReplacement(userId);
        verify(registrationCodeStore, never()).promoteReplacement(userId);
    }

    @Test
    void verifyAndLoginShouldCreateVerifiedUserAndReturnLoginResult(CapturedOutput output) {
        UUID userId = uuid(7);
        UserCredentialView activatedUser = new UserCredentialView(userId, "alice", 1, 0, null, 0L, true, true);

        RefreshCookieSpec cookie = issuedCookie("rt");

        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.verifyForConsumption(userId, "222222", Duration.ofSeconds(60)))
                .thenReturn(RegistrationCodeRepository.VerifyResult.PENDING);
        when(userRegistrationActionApi.createVerifiedRegistrationUser(any(VerifiedRegistrationUserCommand.class))).thenReturn(activatedUser);
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
        verify(registrationDraftRepository).delete("token");
        verify(registrationCodeStore).consumePending(userId);
        verify(loginTokenIssuer).issueLoginResult(activatedUser);
        assertThat(output.getAll())
                .contains("user.id=" + userId)
                .contains("username=alice")
                .doesNotContain("token")
                .doesNotContain("222222");
    }

    @Test
    void verifyAndLoginShouldDeleteDraftWhenLoginIssuanceFailsAfterUserCreation() {
        UUID userId = uuid(7);
        UserCredentialView activatedUser = new UserCredentialView(userId, "alice", 1, 0, null, 0L, true, true);

        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.verifyForConsumption(userId, "222222", Duration.ofSeconds(60)))
                .thenReturn(RegistrationCodeRepository.VerifyResult.PENDING);
        when(userRegistrationActionApi.createVerifiedRegistrationUser(any(VerifiedRegistrationUserCommand.class))).thenReturn(activatedUser);
        when(loginTokenIssuer.issueLoginResult(activatedUser)).thenThrow(new IllegalStateException("token down"));

        assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("token", "222222")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED);

        verify(registrationDraftRepository).delete("token");
        verify(registrationCodeStore).consumePending(userId);
    }

    @Test
    void verifyAndLoginShouldRestorePendingCodeWhenUserCreationFailsBeforeActivation() {
        UUID userId = uuid(7);
        PreparedRegistrationDraft draft = draft(userId);
        when(registrationDraftRepository.find("reg-token")).thenReturn(Optional.of(draft));
        when(registrationCodeStore.verifyForConsumption(draft.userId(), "123456", Duration.ofSeconds(60)))
                .thenReturn(RegistrationCodeRepository.VerifyResult.PENDING);
        RuntimeException createFailure = new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "用户名或邮箱已存在");
        when(userRegistrationActionApi.createVerifiedRegistrationUser(any()))
                .thenThrow(createFailure);

        assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("reg-token", "123456")))
                .isSameAs(createFailure);

        verify(registrationCodeStore).restorePending(draft.userId());
        verify(registrationCodeStore, never()).consumePending(draft.userId());
        verify(registrationDraftRepository, never()).delete("reg-token");
    }

    @Test
    void verifyAndLoginShouldRejectInvalidCodeWithoutIssuingLogin() {
        UUID userId = uuid(7);

        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
        when(registrationCodeStore.verifyForConsumption(userId, "111111", Duration.ofSeconds(60)))
                .thenReturn(RegistrationCodeRepository.VerifyResult.MISMATCH);

        assertThatThrownBy(() -> service.verifyAndLogin(new VerifyRegisterCodeCommand("token", "111111")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CODE_INVALID);

        verify(loginTokenIssuer, never()).issueLoginResult(any(UserCredentialView.class));
        verify(userRegistrationActionApi, never()).createVerifiedRegistrationUser(any());
        verify(registrationDraftRepository, never()).delete(any());
    }

    @Test
    void resendCodeShouldTreatMissingDraftAsStaleContext() {
        doNothing().when(captchaChallenge).requireValidCaptcha("cid", "abcd");
        when(registrationDraftRepository.find("token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);
    }

    @Test
    void resendCodeShouldRejectWhenRegistrationTokenIsMissingOrExpired() {
        doNothing().when(captchaChallenge).requireValidCaptcha("cid", "abcd");
        when(registrationDraftRepository.find("token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);

        verifyNoInteractions(userRegistrationActionApi, registrationCodeStore, mailService);
    }

    @Test
    void resendCodeShouldRejectAndDeleteMalformedDraftBeforeIssuingCode() {
        doNothing().when(captchaChallenge).requireValidCaptcha("cid", "abcd");
        when(registrationDraftRepository.find("token")).thenReturn(Optional.of(new PreparedRegistrationDraft(
                uuid(7),
                "alice",
                "",
                ENCODED_PASSWORD,
                "h",
                Instant.parse("2026-05-03T01:00:00Z"),
                Instant.now().plus(Duration.ofMinutes(30))
        )));

        assertThatThrownBy(() -> service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CONTEXT_INVALID);

        verify(registrationDraftRepository).delete("token");
        verifyNoInteractions(userRegistrationActionApi, registrationCodeStore, mailService);
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
        verifyNoInteractions(userRegistrationActionApi, registrationCodeStore, mailService, loginTokenIssuer);
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
