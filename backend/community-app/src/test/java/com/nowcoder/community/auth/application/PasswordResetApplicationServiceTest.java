package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.ConfirmPasswordResetCommand;
import com.nowcoder.community.auth.application.command.RequestPasswordResetCommand;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.result.PasswordResetRequestResult;
import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.domain.repository.PasswordResetTokenRepository;
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
                userCredentialQueryApi,
                userCredentialActionApi,
                mailService,
                captchaService,
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
                .contains("community.category=security")
                .contains("community.action=password_reset_request")
                .contains("community.outcome=success")
                .contains("user.id=" + userId)
                .contains("masked.email=a***e@example.com")
                .doesNotContain("alice@example.com")
                .doesNotContain("1234")
                .doesNotContain("/#/auth/password/reset?token=");
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
                .contains("community.category=security")
                .contains("community.action=password_reset_request")
                .contains("community.outcome=skipped")
                .contains("community.reason_code=hidden_noop")
                .contains("masked.email=a***e@example.com")
                .doesNotContain("alice@example.com")
                .doesNotContain("1234");
    }

    @Test
    void confirmResetShouldLogSuccessWithoutTokenOrPassword(CapturedOutput output) {
        UUID userId = uuid(7);
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(tokenStore.consume("token-123")).thenReturn(userId);

        boolean result = service.confirmReset(new ConfirmPasswordResetCommand(" token-123 ", " new-password ", "cid", "1234"));

        assertThat(result).isTrue();
        verify(userCredentialActionApi).resetPasswordAndRevokeRefreshSessions(userId, "new-password");
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=password_reset_confirm")
                .contains("community.outcome=success")
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
        when(tokenStore.consume("token-123")).thenReturn(userId, userId);
        doThrow(resetFailure)
                .doNothing()
                .when(userCredentialActionApi).resetPasswordAndRevokeRefreshSessions(userId, "new-password");

        ConfirmPasswordResetCommand command = new ConfirmPasswordResetCommand(" token-123 ", " new-password ", "cid", "1234");
        assertThatThrownBy(() -> service.confirmReset(command)).isSameAs(resetFailure);

        verify(tokenStore).store("token-123", userId, Duration.ofSeconds(600));

        boolean retried = service.confirmReset(command);

        assertThat(retried).isTrue();
    }

    @Test
    void confirmResetShouldValidatePasswordBeforeConsumingToken() {
        BusinessException weakPassword = new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "weak password");
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        doThrow(weakPassword).when(userCredentialActionApi).validatePasswordPolicy("weakpass");

        assertThatThrownBy(() -> service.confirmReset(new ConfirmPasswordResetCommand(" token-123 ", " weakpass ", "cid", "1234")))
                .isSameAs(weakPassword);

        verify(userCredentialActionApi).validatePasswordPolicy("weakpass");
        verify(tokenStore, never()).consume(anyString());
        verify(tokenStore, never()).store(anyString(), any(UUID.class), any(Duration.class));
        verifyNoMoreInteractions(userCredentialActionApi);
    }

    @Test
    void confirmResetShouldLogDeniedWhenTokenIsInvalid(CapturedOutput output) {
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(tokenStore.consume("token-123")).thenReturn(null);

        assertThatThrownBy(() -> service.confirmReset(new ConfirmPasswordResetCommand(" token-123 ", " new-password ", "cid", "1234")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.PASSWORD_RESET_INVALID);

        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=password_reset_confirm")
                .contains("community.outcome=denied")
                .contains("community.reason_code=invalid_token")
                .doesNotContain("token-123")
                .doesNotContain("new-password")
                .doesNotContain("1234");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
