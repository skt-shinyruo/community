package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.UserCredentialService;
import com.nowcoder.community.user.service.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenStore tokenStore;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private UserCredentialService userCredentialService;

    @Mock
    private MailService mailService;

    @Mock
    private CaptchaService captchaService;

    private PasswordResetProperties properties;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        properties = new PasswordResetProperties();
        properties.setResetBaseUrl("https://community.example");
        properties.setTtlSeconds(600);
        properties.setExposeResetLink(false);
        service = new PasswordResetService(
                properties,
                tokenStore,
                userQueryService,
                userCredentialService,
                mailService,
                captchaService
        );
    }

    @Test
    void requestResetShouldLogIssuedEventWithoutResetLink(CapturedOutput output) {
        User user = new User();
        user.setId(7);
        user.setEmail("alice@example.com");
        user.setStatus(1);

        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(userQueryService.findByEmailOrNull("alice@example.com")).thenReturn(user);

        PasswordResetService.RequestResult result = service.requestReset(" alice@example.com ", "cid", "1234");

        assertThat(result.issued()).isTrue();
        assertThat(result.resetLink()).isBlank();
        verify(tokenStore).store(anyString(), eq(7), eq(Duration.ofSeconds(600)));
        verify(mailService).sendPasswordResetMail(eq("alice@example.com"), contains("/#/auth/password/reset?token="));
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=password_reset_request")
                .contains("community.outcome=success")
                .contains("user.id=7")
                .contains("masked.email=a***e@example.com")
                .doesNotContain("alice@example.com")
                .doesNotContain("1234")
                .doesNotContain("/#/auth/password/reset?token=");
    }

    @Test
    void requestResetShouldLogSkippedHiddenNoopForUnknownEmail(CapturedOutput output) {
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(userQueryService.findByEmailOrNull("alice@example.com")).thenReturn(null);

        PasswordResetService.RequestResult result = service.requestReset(" alice@example.com ", "cid", "1234");

        assertThat(result.issued()).isTrue();
        assertThat(result.resetLink()).isBlank();
        verify(tokenStore, never()).store(anyString(), anyInt(), any(Duration.class));
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
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(tokenStore.consume("token-123")).thenReturn(7);

        boolean result = service.confirmReset(" token-123 ", " new-password ", "cid", "1234");

        assertThat(result).isTrue();
        verify(userCredentialService).updatePassword(7, "new-password");
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=password_reset_confirm")
                .contains("community.outcome=success")
                .contains("user.id=7")
                .doesNotContain("token-123")
                .doesNotContain("new-password")
                .doesNotContain("1234");
    }

    @Test
    void confirmResetShouldLogDeniedWhenTokenIsInvalid(CapturedOutput output) {
        when(captchaService.verify("cid", "1234")).thenReturn(true);
        when(tokenStore.consume("token-123")).thenReturn(null);

        assertThatThrownBy(() -> service.confirmReset(" token-123 ", " new-password ", "cid", "1234"))
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
}
