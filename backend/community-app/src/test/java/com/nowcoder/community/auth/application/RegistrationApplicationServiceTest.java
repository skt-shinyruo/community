package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.RegistrationApplicationService.RegisterCommand;
import com.nowcoder.community.auth.application.RegistrationApplicationService.RegisterResult;
import com.nowcoder.community.auth.application.port.RegistrationCodeMailDispatcher;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import com.nowcoder.community.auth.domain.service.AuthSecretGenerator;
import com.nowcoder.community.auth.domain.service.RegistrationDomainService;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PreparedRegistrationUserView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RegistrationApplicationServiceTest {

    private static final String ENCODED_PASSWORD = "$2a$10$7EqJtq98hPqEX7fNZaFWoOHiE9VYh4Vh7H1w52x1x7YjQwlhbR1XK";
    private static final String HEADER_URL = "https://example.com/header.png";

    @Mock
    private UserRegistrationActionApi userRegistrationActionApi;

    @Mock
    private RegistrationCodeMailDispatcher mailDispatcher;

    @Mock
    private CaptchaChallengeComponent captchaChallenge;

    @Mock
    private RegistrationCodeRepository registrationCodeStore;

    @Mock
    private RegistrationDraftRepository registrationDraftRepository;

    @Mock
    private RegistrationRequestRateLimiter registrationRequestRateLimiter;

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
                mailDispatcher,
                captchaChallenge,
                registrationCodeStore,
                registrationDraftRepository,
                new AuthSecretGenerator(),
                new RegistrationDomainService(),
                registrationRequestRateLimiter,
                Clock.systemUTC()
        );
    }

    @Test
    void registerShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.register(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void registerShouldIssueEmailCodeAndReturnMaskedEmailAndDebugCode(CapturedOutput output) {
        UUID userId = uuid(7);
        RegisterCommand command = registerCommand();

        PreparedRegistrationUserView prepared = preparedUser(userId);

        when(userRegistrationActionApi.prepareRegistrationUser("alice", "secret", "alice@example.com")).thenReturn(prepared);
        when(registrationDraftRepository.store(anyString(), any(PreparedRegistrationDraft.class), eq(Duration.ofMinutes(30))))
                .thenReturn(true);
        when(registrationCodeStore.issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)), any(UUID.class)))
                .thenReturn(RegistrationCodeRepository.IssueResult.ISSUED);

        RegisterResult response = service.register(command);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.registrationToken())
                .isNotBlank()
                .hasSizeGreaterThanOrEqualTo(43)
                .matches("[A-Za-z0-9_-]+");
        assertThat(response.emailCodeIssued()).isTrue();
        assertThat(response.maskedEmail()).isNotBlank().contains("@").isNotEqualTo("alice@example.com");
        assertThat(response.debugEmailCode()).matches("\\d{6}");
        verify(userRegistrationActionApi).prepareRegistrationUser("alice", "secret", "alice@example.com");
        verify(registrationRequestRateLimiter).enforce(
                "alice", "alice@example.com", "127.0.0.1");
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PreparedRegistrationDraft> draftCaptor = ArgumentCaptor.forClass(PreparedRegistrationDraft.class);
        verify(registrationDraftRepository).store(tokenCaptor.capture(), draftCaptor.capture(), eq(Duration.ofMinutes(30)));
        assertThat(response.registrationToken()).isEqualTo(tokenCaptor.getValue());
        PreparedRegistrationDraft draft = draftCaptor.getValue();
        assertThat(draft.userId()).isEqualTo(userId);
        assertThat(draft.username()).isEqualTo("alice");
        assertThat(draft.email()).isEqualTo("alice@example.com");
        assertThat(draft.encodedPassword()).isEqualTo(ENCODED_PASSWORD);
        assertThat(draft.headerUrl()).isEqualTo(HEADER_URL);
        assertThat(draft.issuedAt()).isNotNull();
        assertThat(draft.expiresAt()).isEqualTo(draft.issuedAt().plus(Duration.ofMinutes(30)));
        ArgumentCaptor<UUID> deliveryIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(registrationCodeStore).issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)),
                eq(Duration.ofSeconds(60)), deliveryIdCaptor.capture());
        ArgumentCaptor<RegistrationCodeMailDispatcher.Delivery> deliveryCaptor =
                ArgumentCaptor.forClass(RegistrationCodeMailDispatcher.Delivery.class);
        verify(mailDispatcher).dispatch(deliveryCaptor.capture());
        assertThat(deliveryCaptor.getValue().deliveryId()).isEqualTo(deliveryIdCaptor.getValue());
        assertThat(deliveryCaptor.getValue().registrationId()).isEqualTo(userId);
        assertThat(deliveryCaptor.getValue().replacementLeaseId()).isNull();
        assertThat(deliveryCaptor.getValue().toEmail()).isEqualTo("alice@example.com");
        assertThat(deliveryCaptor.getValue().code()).matches("\\d{6}");
        assertThat(deliveryCaptor.getValue().expiresAt()).isAfter(java.time.Instant.now());
        assertThat(output.getAll())
                .contains("user.id=" + userId)
                .contains("username=alice")
                .contains("masked.email=" + response.maskedEmail())
                .doesNotContain("secret")
                .doesNotContain("alice@example.com")
                .doesNotContain(response.registrationToken())
                .doesNotContain(response.debugEmailCode());
    }

    @Test
    void registerShouldRollbackDraftAndCodeWhenMailDispatchFails() {
        UUID userId = uuid(8);
        RegisterCommand command = registerCommand();

        PreparedRegistrationUserView prepared = preparedUser(userId);

        when(userRegistrationActionApi.prepareRegistrationUser("alice", "secret", "alice@example.com")).thenReturn(prepared);
        when(registrationDraftRepository.store(anyString(), any(PreparedRegistrationDraft.class), eq(Duration.ofMinutes(30))))
                .thenReturn(true);
        when(registrationCodeStore.issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)), any(UUID.class)))
                .thenReturn(RegistrationCodeRepository.IssueResult.ISSUED);
        doThrow(new IllegalStateException("mail down"))
                .when(mailDispatcher).dispatch(any(RegistrationCodeMailDispatcher.Delivery.class));

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mail down");

        verify(registrationDraftRepository).store(anyString(), any(PreparedRegistrationDraft.class), eq(Duration.ofMinutes(30)));
        verify(registrationCodeStore).issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)),
                eq(Duration.ofSeconds(60)), any(UUID.class));
        verify(registrationDraftRepository).delete(matches("[A-Za-z0-9_-]+"));
        verify(registrationCodeStore).delete(userId);
    }

    @Test
    void registerShouldFailBeforeIssuingCodeWhenRegistrationDraftStoreRejectsGeneratedTokens() {
        UUID userId = uuid(9);
        RegisterCommand command = registerCommand();

        PreparedRegistrationUserView prepared = preparedUser(userId);

        when(userRegistrationActionApi.prepareRegistrationUser("alice", "secret", "alice@example.com")).thenReturn(prepared);
        when(registrationDraftRepository.store(anyString(), any(PreparedRegistrationDraft.class), eq(Duration.ofMinutes(30)))).thenReturn(false);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INTERNAL_ERROR);

        verify(registrationDraftRepository, times(5)).store(anyString(), any(PreparedRegistrationDraft.class), eq(Duration.ofMinutes(30)));
        verify(registrationCodeStore, never()).issue(any(), any(), any(), any(), any());
        verify(mailDispatcher, never()).dispatch(any());
        verify(registrationCodeStore).delete(userId);
        verify(registrationDraftRepository, never()).delete(any());
    }

    @Test
    void registerShouldRollbackDraftAndCodeWhenCodeIssueReturnsNonIssued() {
        UUID userId = uuid(10);
        RegisterCommand command = registerCommand();

        PreparedRegistrationUserView prepared = preparedUser(userId);

        when(userRegistrationActionApi.prepareRegistrationUser("alice", "secret", "alice@example.com")).thenReturn(prepared);
        when(registrationDraftRepository.store(anyString(), any(PreparedRegistrationDraft.class), eq(Duration.ofMinutes(30))))
                .thenReturn(true);
        when(registrationCodeStore.issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)), any(UUID.class)))
                .thenReturn(RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INTERNAL_ERROR);

        verify(registrationDraftRepository).delete(matches("[A-Za-z0-9_-]+"));
        verify(registrationCodeStore).delete(userId);
        verify(mailDispatcher, never()).dispatch(any());
    }

    @Test
    void registerShouldRollbackDraftAndCodeWhenCodeIssueThrows() {
        UUID userId = uuid(11);
        RegisterCommand command = registerCommand();

        PreparedRegistrationUserView prepared = preparedUser(userId);

        when(userRegistrationActionApi.prepareRegistrationUser("alice", "secret", "alice@example.com")).thenReturn(prepared);
        when(registrationDraftRepository.store(anyString(), any(PreparedRegistrationDraft.class), eq(Duration.ofMinutes(30))))
                .thenReturn(true);
        when(registrationCodeStore.issue(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60)), any(UUID.class)))
                .thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis down");

        verify(registrationDraftRepository).delete(matches("[A-Za-z0-9_-]+"));
        verify(registrationCodeStore).delete(userId);
        verify(mailDispatcher, never()).dispatch(any());
    }

    @Test
    void registerShouldCleanupCodeOnlyWhenRegistrationDraftCreationThrows() {
        UUID userId = uuid(12);
        RegisterCommand command = registerCommand();

        PreparedRegistrationUserView prepared = preparedUser(userId);

        when(userRegistrationActionApi.prepareRegistrationUser("alice", "secret", "alice@example.com")).thenReturn(prepared);
        when(registrationDraftRepository.store(anyString(), any(PreparedRegistrationDraft.class), eq(Duration.ofMinutes(30))))
                .thenThrow(new IllegalStateException("draft down"));

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("draft down");

        verify(registrationCodeStore, never()).issue(any(), any(), any(), any(), any());
        verify(mailDispatcher, never()).dispatch(any());
        verify(registrationCodeStore).delete(userId);
        verify(registrationDraftRepository, never()).delete(any());
    }

    @Test
    void registerShouldRejectInvalidPreparedMaterialBeforeIssuingDraftOrCode() {
        UUID userId = uuid(13);
        RegisterCommand command = registerCommand();

        PreparedRegistrationUserView prepared = new PreparedRegistrationUserView(userId, "alice", " ", ENCODED_PASSWORD, HEADER_URL);

        when(userRegistrationActionApi.prepareRegistrationUser("alice", "secret", "alice@example.com")).thenReturn(prepared);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INTERNAL_ERROR);

        verify(registrationDraftRepository, never()).store(any(), any(), any());
        verify(registrationCodeStore, never()).issue(any(), any(), any(), any(), any());
        verify(mailDispatcher, never()).dispatch(any());
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static RegisterCommand registerCommand() {
        return new RegisterCommand(
                "alice", "secret", "alice@example.com", "cid", "abcd", "127.0.0.1");
    }

    private static PreparedRegistrationUserView preparedUser(UUID userId) {
        return new PreparedRegistrationUserView(userId, "alice", "alice@example.com", ENCODED_PASSWORD, HEADER_URL);
    }
}
