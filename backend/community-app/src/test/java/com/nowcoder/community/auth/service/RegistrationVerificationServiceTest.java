package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.dto.RegisterCodeResendResponse;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.service.UserRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RegistrationVerificationServiceTest {

    @Mock
    private UserRegistrationService userRegistrationService;

    @Mock
    private RegistrationCodeStore registrationCodeStore;

    @Mock
    private MailService mailService;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private AuthService authService;

    @Mock
    private RegistrationSessionStore registrationSessionStore;

    private RegistrationProperties properties;
    private RegistrationVerificationService service;

    @BeforeEach
    void setUp() {
        properties = new RegistrationProperties();
        properties.getCode().setExposeCode(true);
        properties.getCode().setTtlSeconds(600);
        service = new RegistrationVerificationService(
                userRegistrationService,
                properties,
                registrationCodeStore,
                mailService,
                captchaService,
                registrationSessionStore,
                authService
        );
    }

    @Test
    void resendCodeShouldRequireCaptchaAndReturnIssuedResponse() {
        User user = new User();
        user.setId(7);
        user.setEmail("alice@example.com");
        user.setStatus(0);

        when(captchaService.verify("cid", "abcd")).thenReturn(true);
        when(registrationSessionStore.findUserId("token")).thenReturn(7);
        when(userRegistrationService.getPendingRegistrationUser(7, Duration.ofMinutes(30))).thenReturn(user);
        when(registrationCodeStore.issue(eq(7), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60))))
                .thenReturn(RegistrationCodeStore.IssueResult.ISSUED);

        RegisterCodeResendResponse response = service.resendCode("token", "cid", "abcd");

        assertThat(response.isIssued()).isTrue();
        assertThat(response.getMaskedEmail()).isNotBlank().contains("@").isNotEqualTo("alice@example.com");
        assertThat(response.getDebugEmailCode()).matches("\\d{6}");
        verify(registrationCodeStore).issue(eq(7), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)));
        verify(mailService).sendRegistrationCodeMail(eq("alice@example.com"), matches("\\d{6}"));
    }

    @Test
    void resendCodeShouldRejectWhenCooldownWindowIsStillActive() {
        User user = new User();
        user.setId(7);
        user.setEmail("alice@example.com");
        user.setStatus(0);

        when(captchaService.verify("cid", "abcd")).thenReturn(true);
        when(registrationSessionStore.findUserId("token")).thenReturn(7);
        when(userRegistrationService.getPendingRegistrationUser(7, Duration.ofMinutes(30))).thenReturn(user);
        when(registrationCodeStore.issue(eq(7), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60))))
                .thenReturn(RegistrationCodeStore.IssueResult.COOLDOWN_ACTIVE);

        assertThatThrownBy(() -> service.resendCode("token", "cid", "abcd"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CODE_RESEND_COOLDOWN);

        verifyNoInteractions(mailService);
    }

    @Test
    void verifyAndLoginShouldActivateInactiveUserAndReturnLoginResult(CapturedOutput output) {
        User user = new User();
        user.setId(7);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setStatus(0);

        ResponseCookie cookie = ResponseCookie.from("refresh_token", "rt").path("/api/auth").build();

        when(registrationSessionStore.findUserId("token")).thenReturn(7);
        when(userRegistrationService.getPendingRegistrationUser(7, Duration.ofMinutes(30))).thenReturn(user);
        when(registrationCodeStore.verifyAndConsume(7, "222222")).thenReturn(RegistrationCodeStore.VerifyResult.SUCCESS);
        when(authService.issueLoginResult(any(User.class))).thenReturn(new AuthService.LoginResult("access-token", cookie));

        AuthService.LoginResult result = service.verifyAndLogin("token", "222222");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshCookie()).isEqualTo(cookie);
        verify(userRegistrationService).activateUser(7);
        verify(registrationSessionStore).delete("token");
        verify(authService).issueLoginResult(eq(user));
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=registration_verify")
                .contains("community.outcome=success")
                .contains("user.id=7")
                .contains("username=alice")
                .doesNotContain("token")
                .doesNotContain("222222");
    }

    @Test
    void verifyAndLoginShouldRejectInvalidCodeWithoutIssuingLogin() {
        User user = new User();
        user.setId(7);
        user.setUsername("alice");
        user.setStatus(0);

        when(registrationSessionStore.findUserId("token")).thenReturn(7);
        when(userRegistrationService.getPendingRegistrationUser(7, Duration.ofMinutes(30))).thenReturn(user);
        when(registrationCodeStore.verifyAndConsume(7, "111111")).thenReturn(RegistrationCodeStore.VerifyResult.MISMATCH);

        assertThatThrownBy(() -> service.verifyAndLogin("token", "111111"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REGISTRATION_CODE_INVALID);

        verify(authService, never()).issueLoginResult(any(User.class));
        verify(userRegistrationService, never()).activateUser(anyInt());
        verify(registrationSessionStore, never()).delete(any());
    }

    @Test
    void resendCodeShouldTreatExpiredPendingUserAsStaleContext() {
        when(captchaService.verify("cid", "abcd")).thenReturn(true);
        when(registrationSessionStore.findUserId("token")).thenReturn(7);
        when(userRegistrationService.getPendingRegistrationUser(7, Duration.ofMinutes(30)))
                .thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND, "注册已过期，请重新注册"));

        assertThatThrownBy(() -> service.resendCode("token", "cid", "abcd"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void resendCodeShouldRejectWhenRegistrationTokenIsMissingOrExpired() {
        when(captchaService.verify("cid", "abcd")).thenReturn(true);
        when(registrationSessionStore.findUserId("token")).thenReturn(null);

        assertThatThrownBy(() -> service.resendCode("token", "cid", "abcd"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(userRegistrationService, registrationCodeStore, mailService);
    }
}
