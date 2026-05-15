package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.ConfirmPasswordResetCommand;
import com.nowcoder.community.auth.application.command.RequestPasswordResetCommand;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.result.PasswordResetRequestResult;
import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import com.nowcoder.community.auth.domain.repository.PasswordResetTokenRepository;
import com.nowcoder.community.auth.domain.service.AuthSecretGenerator;
import com.nowcoder.community.auth.domain.service.PasswordResetDomainService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.action.UserCredentialActionApi;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PasswordResetApplicationServiceTest {

    @Mock
    private PasswordResetTokenRepository tokenStore;

    @Mock
    private LoginRateLimitRepository resetRequestRateLimitRepository;

    @Mock
    private UserCredentialQueryApi userCredentialQueryApi;

    @Mock
    private UserCredentialActionApi userCredentialActionApi;

    @Mock
    private MailPort mailService;

    @Mock
    private CaptchaApplicationService captchaService;

    private PasswordResetProperties properties;
    private PasswordResetApplicationService service;

    @BeforeEach
    void setUp() {
        properties = new PasswordResetProperties();
        properties.setResetBaseUrl("https://community.example");
        properties.setTtlSeconds(600);
        service = new PasswordResetApplicationService(
                properties,
                tokenStore,
                resetRequestRateLimitRepository,
                userCredentialQueryApi,
                userCredentialActionApi,
                mailService,
                captchaService,
                new AuthSecretGenerator(),
                new PasswordResetDomainService()
        );
    }

    @Test
    void requestResetShouldLogIssuedEventWithoutResetLink(CapturedOutput output) {
        UUID userId = uuid(7);
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, null);

        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(userCredentialQueryApi.findByEmailOrNull("alice@example.com")).thenReturn(user);

        PasswordResetRequestResult result = service.requestReset(new RequestPasswordResetCommand(" alice@example.com ", "cid", "1234"));

        assertThat(result.issued()).isTrue();
        assertThat(result.resetLink()).isBlank();
        verify(tokenStore).store(anyString(), eq(userId), eq(Duration.ofSeconds(600)));
        verify(mailService).sendPasswordResetMail(eq("alice@example.com"), contains("/#/auth/password/reset?token="));
        assertThat(output.getAll())
                .contains("user.id=" + userId)
                .contains("masked.email=a***e@example.com")
                .doesNotContain("alice@example.com")
                .doesNotContain("1234")
                .doesNotContain("/#/auth/password/reset?token=");
    }

    @Test
    void requestResetShouldIssueAtLeast256BitUrlSafeToken() {
        UUID userId = uuid(7);
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, null);
        String[] capturedToken = new String[1];

        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(userCredentialQueryApi.findByEmailOrNull("alice@example.com")).thenReturn(user);
        doAnswer(invocation -> {
            capturedToken[0] = invocation.getArgument(0);
            return null;
        }).when(tokenStore).store(anyString(), eq(userId), eq(Duration.ofSeconds(600)));

        service.requestReset(new RequestPasswordResetCommand(" alice@example.com ", "cid", "1234"));

        assertThat(capturedToken[0])
                .hasSizeGreaterThanOrEqualTo(43)
                .matches("[A-Za-z0-9_-]+")
                .doesNotContain("=");
        verify(mailService).sendPasswordResetMail(eq("alice@example.com"), contains("token=" + capturedToken[0]));
    }

    @Test
    void requestResetShouldDeleteIssuedTokenWhenMailSendingFails() {
        UUID userId = uuid(7);
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, null);
        String[] capturedToken = new String[1];

        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(userCredentialQueryApi.findByEmailOrNull("alice@example.com")).thenReturn(user);
        doAnswer(invocation -> {
            capturedToken[0] = invocation.getArgument(0);
            return null;
        }).when(tokenStore).store(anyString(), eq(userId), eq(Duration.ofSeconds(600)));
        doThrow(new IllegalStateException("mail down"))
                .when(mailService).sendPasswordResetMail(eq("alice@example.com"), contains("/#/auth/password/reset?token="));

        assertThatThrownBy(() -> service.requestReset(new RequestPasswordResetCommand("alice@example.com", "cid", "1234")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mail down");

        assertThat(capturedToken[0]).isNotBlank();
        verify(tokenStore).delete(capturedToken[0]);
    }

    @Test
    void requestResetShouldRejectTooManyRequestsBeforeLookingUpUser() {
        properties.setRequestWindowSeconds(300);
        properties.setMaxRequestsPerEmail(1);
        properties.setMaxRequestsPerIp(20);
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(resetRequestRateLimitRepository.increment("auth:pwdreset:req:email:alice@example.com", 300)).thenReturn(2);

        assertThatThrownBy(() -> service.requestReset(new RequestPasswordResetCommand(
                " alice@example.com ",
                "cid",
                "1234",
                "203.0.113.10"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);

        verify(userCredentialQueryApi, never()).findByEmailOrNull(anyString());
        verify(tokenStore, never()).store(anyString(), any(UUID.class), any(Duration.class));
        verify(mailService, never()).sendPasswordResetMail(anyString(), anyString());
    }

    @Test
    void requestResetShouldLogSkippedHiddenNoopForUnknownEmail(CapturedOutput output) {
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(userCredentialQueryApi.findByEmailOrNull("alice@example.com")).thenReturn(null);

        PasswordResetRequestResult result = service.requestReset(new RequestPasswordResetCommand(" alice@example.com ", "cid", "1234"));

        assertThat(result.issued()).isTrue();
        assertThat(result.resetLink()).isBlank();
        verify(tokenStore, never()).store(anyString(), any(UUID.class), any(Duration.class));
        verify(mailService, never()).sendPasswordResetMail(anyString(), anyString());
        assertThat(output.getAll())
                .contains("community.reason_code=hidden_noop")
                .contains("masked.email=a***e@example.com")
                .doesNotContain("alice@example.com")
                .doesNotContain("1234");
    }

    @Test
    void confirmResetShouldLogSuccessWithoutTokenOrPassword(CapturedOutput output) {
        UUID userId = uuid(7);
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(tokenStore.consumeWithTtl("token-123"))
                .thenReturn(new PasswordResetTokenRepository.ConsumedPasswordResetToken(userId, Duration.ofSeconds(600)));

        boolean result = service.confirmReset(new ConfirmPasswordResetCommand(" token-123 ", " new-password ", "cid", "1234"));

        assertThat(result).isTrue();
        verify(userCredentialActionApi).validatePasswordPolicy(" new-password ");
        verify(userCredentialActionApi).resetPasswordAndRevokeRefreshSessions(userId, " new-password ");
        assertThat(output.getAll())
                .contains("user.id=" + userId)
                .doesNotContain("token-123")
                .doesNotContain("new-password")
                .doesNotContain("1234");
    }

    @Test
    void confirmResetShouldRestoreConsumedTokenWhenUserResetFailsSoRetryCanSucceed() {
        UUID userId = uuid(7);
        RuntimeException resetFailure = new IllegalStateException("reset failed");
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(tokenStore.consumeWithTtl("token-123")).thenReturn(
                new PasswordResetTokenRepository.ConsumedPasswordResetToken(userId, Duration.ofSeconds(600)),
                new PasswordResetTokenRepository.ConsumedPasswordResetToken(userId, Duration.ofSeconds(600))
        );
        doThrow(resetFailure)
                .doNothing()
                .when(userCredentialActionApi).resetPasswordAndRevokeRefreshSessions(userId, " new-password ");

        ConfirmPasswordResetCommand command = new ConfirmPasswordResetCommand(" token-123 ", " new-password ", "cid", "1234");
        assertThatThrownBy(() -> service.confirmReset(command)).isSameAs(resetFailure);

        verify(tokenStore).store("token-123", userId, Duration.ofSeconds(600));

        boolean retried = service.confirmReset(command);

        assertThat(retried).isTrue();
    }

    @Test
    void confirmResetShouldRestoreConsumedTokenWithRemainingTtlWhenUserResetFails() {
        UUID userId = uuid(8);
        RuntimeException resetFailure = new IllegalStateException("reset failed");
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(tokenStore.consumeWithTtl("token-123"))
                .thenReturn(new PasswordResetTokenRepository.ConsumedPasswordResetToken(userId, Duration.ofSeconds(123)));
        doThrow(resetFailure).when(userCredentialActionApi).resetPasswordAndRevokeRefreshSessions(userId, " new-password ");

        ConfirmPasswordResetCommand command = new ConfirmPasswordResetCommand(" token-123 ", " new-password ", "cid", "1234");
        assertThatThrownBy(() -> service.confirmReset(command)).isSameAs(resetFailure);

        verify(tokenStore).store("token-123", userId, Duration.ofSeconds(123));
    }

    @Test
    void confirmResetShouldValidatePasswordBeforeConsumingToken() {
        BusinessException weakPassword = new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "weak password");
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        doThrow(weakPassword).when(userCredentialActionApi).validatePasswordPolicy(" weakpass ");

        assertThatThrownBy(() -> service.confirmReset(new ConfirmPasswordResetCommand(" token-123 ", " weakpass ", "cid", "1234")))
                .isSameAs(weakPassword);

        verify(userCredentialActionApi).validatePasswordPolicy(" weakpass ");
        verify(tokenStore, never()).consume(anyString());
        verify(tokenStore, never()).store(anyString(), any(UUID.class), any(Duration.class));
        verifyNoMoreInteractions(userCredentialActionApi);
    }

    @Test
    void confirmResetShouldLogDeniedWhenTokenIsInvalid(CapturedOutput output) {
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(tokenStore.consumeWithTtl("token-123")).thenReturn(null);

        assertThatThrownBy(() -> service.confirmReset(new ConfirmPasswordResetCommand(" token-123 ", " new-password ", "cid", "1234")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.PASSWORD_RESET_INVALID);

        assertThat(output.getAll())
                .contains("community.reason_code=invalid_token")
                .doesNotContain("token-123")
                .doesNotContain("new-password")
                .doesNotContain("1234");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
