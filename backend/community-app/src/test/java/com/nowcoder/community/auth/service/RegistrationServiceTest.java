package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.dto.RegisterRequest;
import com.nowcoder.community.auth.dto.RegisterResponse;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.InternalUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private InternalUserService internalUserService;

    @Mock
    private MailService mailService;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private RegistrationCodeStore registrationCodeStore;

    private RegistrationProperties properties;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        properties = new RegistrationProperties();
        properties.getCode().setExposeCode(true);
        properties.getCode().setTtlSeconds(600);
        service = new RegistrationService(internalUserService, properties, mailService, captchaService, registrationCodeStore);
    }

    @Test
    void registerShouldIssueEmailCodeAndReturnMaskedEmailAndDebugCode() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("secret");
        request.setEmail("alice@example.com");
        request.setCaptchaId("cid");
        request.setCaptchaCode("abcd");

        User created = new User();
        created.setId(7);
        created.setEmail("alice@example.com");

        when(captchaService.verify("cid", "abcd")).thenReturn(true);
        when(internalUserService.register("alice", "secret", "alice@example.com", Duration.ofMinutes(30))).thenReturn(created);
        when(registrationCodeStore.issue(eq(7), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60))))
                .thenReturn(RegistrationCodeStore.IssueResult.ISSUED);

        HttpServletRequest httpRequest = new MockHttpServletRequest();
        RegisterResponse response = service.register(request, httpRequest);

        assertThat(response.getUserId()).isEqualTo(7);
        assertThat(response.isEmailCodeIssued()).isTrue();
        assertThat(response.getMaskedEmail()).isNotBlank().contains("@").isNotEqualTo("alice@example.com");
        assertThat(response.getDebugEmailCode()).matches("\\d{6}");
        verify(internalUserService).register("alice", "secret", "alice@example.com", Duration.ofMinutes(30));
        verify(registrationCodeStore).issue(eq(7), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)));
        verify(mailService).sendRegistrationCodeMail(eq("alice@example.com"), matches("\\d{6}"));
    }
}
