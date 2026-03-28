package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.dto.RegisterRequest;
import com.nowcoder.community.auth.dto.RegisterResponse;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PendingRegistrationUserView;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RegistrationServiceTest {

    @Mock
    private UserRegistrationActionApi userRegistrationActionApi;

    @Mock
    private MailService mailService;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private RegistrationCodeStore registrationCodeStore;

    @Mock
    private RegistrationSessionStore registrationSessionStore;

    private RegistrationProperties properties;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        properties = new RegistrationProperties();
        properties.getCode().setExposeCode(true);
        properties.getCode().setTtlSeconds(600);
        service = new RegistrationService(
                userRegistrationActionApi,
                properties,
                mailService,
                captchaService,
                registrationCodeStore,
                registrationSessionStore
        );
    }

    @Test
    void registerShouldIssueEmailCodeAndReturnMaskedEmailAndDebugCode(CapturedOutput output) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("secret");
        request.setEmail("alice@example.com");
        request.setCaptchaId("cid");
        request.setCaptchaCode("abcd");

        PendingRegistrationUserView created = new PendingRegistrationUserView(7, "alice", "alice@example.com", 0, 0, null);

        when(captchaService.verify("cid", "abcd")).thenReturn(true);
        when(userRegistrationActionApi.registerPendingUser("alice", "secret", "alice@example.com", Duration.ofMinutes(30))).thenReturn(created);
        when(registrationCodeStore.issue(eq(7), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60))))
                .thenReturn(RegistrationCodeStore.IssueResult.ISSUED);
        when(registrationSessionStore.issue(eq(7), eq(Duration.ofMinutes(30))))
                .thenReturn("0123456789abcdef0123456789abcdef");

        HttpServletRequest httpRequest = new MockHttpServletRequest();
        RegisterResponse response = service.register(request, httpRequest);

        assertThat(response.getUserId()).isEqualTo(7);
        assertThat(response.getRegistrationToken()).isNotBlank().matches("[a-f0-9]{32}");
        assertThat(response.isEmailCodeIssued()).isTrue();
        assertThat(response.getMaskedEmail()).isNotBlank().contains("@").isNotEqualTo("alice@example.com");
        assertThat(response.getDebugEmailCode()).matches("\\d{6}");
        verify(userRegistrationActionApi).registerPendingUser("alice", "secret", "alice@example.com", Duration.ofMinutes(30));
        verify(registrationCodeStore).issue(eq(7), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)));
        verify(registrationSessionStore).issue(eq(7), eq(Duration.ofMinutes(30)));
        verify(mailService).sendRegistrationCodeMail(eq("alice@example.com"), matches("\\d{6}"));
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=registration_code_issue")
                .contains("community.outcome=success")
                .contains("user.id=7")
                .contains("username=alice")
                .contains("masked.email=" + response.getMaskedEmail())
                .doesNotContain("secret")
                .doesNotContain("alice@example.com")
                .doesNotContain(response.getRegistrationToken())
                .doesNotContain(response.getDebugEmailCode());
    }
}
