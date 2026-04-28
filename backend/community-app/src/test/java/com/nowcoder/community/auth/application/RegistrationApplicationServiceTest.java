package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.RegisterCommand;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.result.RegisterResult;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import com.nowcoder.community.auth.domain.repository.RegistrationSessionRepository;
import com.nowcoder.community.auth.domain.service.RegistrationDomainService;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PendingRegistrationUserView;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RegistrationApplicationServiceTest {

    @Mock
    private UserRegistrationActionApi userRegistrationActionApi;

    @Mock
    private MailPort mailService;

    @Mock
    private CaptchaApplicationService captchaService;

    @Mock
    private RegistrationCodeRepository registrationCodeStore;

    @Mock
    private RegistrationSessionRepository registrationSessionStore;

    private RegistrationProperties properties;
    private RegistrationApplicationService service;

    @BeforeEach
    void setUp() {
        properties = new RegistrationProperties();
        properties.getCode().setExposeCode(true);
        properties.getCode().setTtlSeconds(600);
        service = new RegistrationApplicationService(
                userRegistrationActionApi,
                properties,
                mailService,
                captchaService,
                registrationCodeStore,
                registrationSessionStore,
                new RegistrationDomainService()
        );
    }

    @Test
    void registerShouldIssueEmailCodeAndReturnMaskedEmailAndDebugCode(CapturedOutput output) {
        UUID userId = uuid(7);
        RegisterCommand command = registerCommand();

        PendingRegistrationUserView created = new PendingRegistrationUserView(userId, "alice", "alice@example.com", 0, 0, null);

        when(captchaService.verify("cid", "abcd")).thenReturn(true);
        when(userRegistrationActionApi.registerPendingUser("alice", "secret", "alice@example.com", Duration.ofMinutes(30))).thenReturn(created);
        when(registrationCodeStore.issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60))))
                .thenReturn(RegistrationCodeRepository.IssueResult.ISSUED);
        when(registrationSessionStore.issue(eq(userId), eq(Duration.ofMinutes(30))))
                .thenReturn("0123456789abcdef0123456789abcdef");

        RegisterResult response = service.register(command);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.registrationToken()).isNotBlank().matches("[a-f0-9]{32}");
        assertThat(response.emailCodeIssued()).isTrue();
        assertThat(response.maskedEmail()).isNotBlank().contains("@").isNotEqualTo("alice@example.com");
        assertThat(response.debugEmailCode()).matches("\\d{6}");
        verify(userRegistrationActionApi).registerPendingUser("alice", "secret", "alice@example.com", Duration.ofMinutes(30));
        verify(registrationCodeStore).issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)));
        verify(registrationSessionStore).issue(eq(userId), eq(Duration.ofMinutes(30)));
        verify(mailService).sendRegistrationCodeMail(eq("alice@example.com"), matches("\\d{6}"));
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=registration_code_issue")
                .contains("community.outcome=success")
                .contains("user.id=" + userId)
                .contains("username=alice")
                .contains("masked.email=" + response.maskedEmail())
                .doesNotContain("secret")
                .doesNotContain("alice@example.com")
                .doesNotContain(response.registrationToken())
                .doesNotContain(response.debugEmailCode());
    }

    @Test
    void registerShouldRollbackPendingUserAndArtifactsWhenMailSendingFails() {
        UUID userId = uuid(8);
        RegisterCommand command = registerCommand();

        PendingRegistrationUserView created = new PendingRegistrationUserView(userId, "alice", "alice@example.com", 0, 0, null);

        when(captchaService.verify("cid", "abcd")).thenReturn(true);
        when(userRegistrationActionApi.registerPendingUser("alice", "secret", "alice@example.com", Duration.ofMinutes(30))).thenReturn(created);
        when(registrationSessionStore.issue(eq(userId), eq(Duration.ofMinutes(30))))
                .thenReturn("0123456789abcdef0123456789abcdef");
        when(registrationCodeStore.issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60))))
                .thenReturn(RegistrationCodeRepository.IssueResult.ISSUED);
        doThrow(new IllegalStateException("mail down"))
                .when(mailService).sendRegistrationCodeMail(eq("alice@example.com"), matches("\\d{6}"));

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mail down");

        verify(registrationSessionStore).issue(eq(userId), eq(Duration.ofMinutes(30)));
        verify(registrationCodeStore).issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)));
        verify(registrationSessionStore).delete("0123456789abcdef0123456789abcdef");
        verify(registrationCodeStore).delete(userId);
        verify(userRegistrationActionApi).deletePendingUser(userId);
    }

    @Test
    void registerShouldFailBeforeIssuingCodeWhenRegistrationSessionCreationFails() {
        UUID userId = uuid(9);
        RegisterCommand command = registerCommand();

        PendingRegistrationUserView created = new PendingRegistrationUserView(userId, "alice", "alice@example.com", 0, 0, null);

        when(captchaService.verify("cid", "abcd")).thenReturn(true);
        when(userRegistrationActionApi.registerPendingUser("alice", "secret", "alice@example.com", Duration.ofMinutes(30))).thenReturn(created);
        when(registrationSessionStore.issue(eq(userId), eq(Duration.ofMinutes(30)))).thenReturn(null);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR);

        verify(registrationSessionStore).issue(eq(userId), eq(Duration.ofMinutes(30)));
        verify(registrationCodeStore, never()).issue(any(), any(), any(), any());
        verify(mailService, never()).sendRegistrationCodeMail(any(), any());
        verify(registrationCodeStore).delete(userId);
        verify(userRegistrationActionApi).deletePendingUser(userId);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static RegisterCommand registerCommand() {
        return new RegisterCommand("alice", "secret", "alice@example.com", "cid", "abcd");
    }
}
