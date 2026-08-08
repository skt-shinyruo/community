package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.ConfirmPasswordResetCommand;
import com.nowcoder.community.auth.application.command.RequestPasswordResetCommand;
import com.nowcoder.community.auth.application.port.PasswordResetMailDispatcher;
import com.nowcoder.community.auth.application.port.PasswordResetTransactionCompletion;
import com.nowcoder.community.auth.application.result.PasswordResetRequestResult;
import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PasswordResetApplicationServiceTest {

    private static final UUID LEASE_ID = uuid(99);

    @Mock
    private PasswordResetTokenRepository tokenStore;
    @Mock
    private LoginRateLimitRepository resetRequestRateLimitRepository;
    @Mock
    private UserCredentialQueryApi userCredentialQueryApi;
    @Mock
    private UserCredentialActionApi userCredentialActionApi;
    @Mock
    private PasswordResetMailDispatcher passwordResetMailDispatcher;
    @Mock
    private PasswordResetTransactionCompletion transactionCompletion;
    @Mock
    private CaptchaChallengeComponent captchaChallenge;

    private PasswordResetProperties properties;
    private PasswordResetTokenDeriver tokenDeriver;
    private PasswordResetApplicationService service;

    @BeforeEach
    void setUp() {
        properties = new PasswordResetProperties();
        properties.setResetBaseUrl("https://community.example");
        properties.setTtlSeconds(600);
        properties.setIdentifierHmacSecret("test-password-reset-hmac-secret");
        properties.setQuotaHmacSecret("stable-password-reset-quota-hmac-secret");
        tokenDeriver = new PasswordResetTokenDeriver(properties);
        service = new PasswordResetApplicationService(
                properties,
                tokenStore,
                resetRequestRateLimitRepository,
                userCredentialQueryApi,
                userCredentialActionApi,
                passwordResetMailDispatcher,
                transactionCompletion,
                captchaChallenge,
                tokenDeriver,
                new PasswordResetDomainService()
        );
    }

    @Test
    void requestResetShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.requestReset(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void confirmResetShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.confirmReset(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void requestResetResultShouldExposeOnlyIssued() {
        assertThat(Arrays.stream(PasswordResetRequestResult.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("issued");
    }

    @Test
    void requestResetShouldStoreStrongTokenAndDispatchToPersistedCanonicalEmail(CapturedOutput output) {
        UUID userId = uuid(7);
        UserCredentialView user = credential(userId, "Alice.Canonical@example.com", 17L);
        when(userCredentialQueryApi.findByEmailOrNull("alice@example.com")).thenReturn(user);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> deliveryId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> derivationKeyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> deliveryReference = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> expiresAt = ArgumentCaptor.forClass(Instant.class);

        PasswordResetRequestResult result = service.requestReset(new RequestPasswordResetCommand(
                " ALICE@example.com ",
                "cid",
                "1234"
        ));

        assertThat(result.issued()).isTrue();
        verify(tokenStore).store(token.capture(), eq(userId), eq(17L), eq(Duration.ofSeconds(600)));
        assertThat(token.getValue())
                .hasSizeGreaterThanOrEqualTo(43)
                .matches("[A-Za-z0-9_-]+")
                .doesNotContain("=");
        verify(passwordResetMailDispatcher).dispatch(
                deliveryId.capture(),
                derivationKeyId.capture(),
                deliveryReference.capture(),
                eq("Alice.Canonical@example.com"),
                expiresAt.capture()
        );
        PasswordResetTokenDeriver.DeliveryMaterial delivery = tokenDeriver.deriveDelivery(deliveryId.getValue());
        assertThat(token.getValue()).isEqualTo(delivery.token());
        assertThat(derivationKeyId.getValue()).isEqualTo(delivery.derivationKeyId());
        assertThat(deliveryReference.getValue()).isEqualTo(delivery.deliveryReference());
        assertThat(expiresAt.getValue()).isAfter(Instant.now().plusSeconds(500));
        assertThat(output.getAll())
                .contains("user.id=" + userId)
                .doesNotContain(token.getValue())
                .doesNotContain("Alice.Canonical@example.com");
    }

    @Test
    void requestResetShouldFailAndDeleteTokenWhenDurableDispatchCannotBeStored() {
        UUID userId = uuid(8);
        when(userCredentialQueryApi.findByEmailOrNull("alice@example.com"))
                .thenReturn(credential(userId, "alice@example.com", 18L));
        doThrow(new IllegalStateException("executor saturated"))
                .when(passwordResetMailDispatcher).dispatch(
                        any(UUID.class), anyString(), anyString(), eq("alice@example.com"), any(Instant.class));
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);

        assertThatThrownBy(() -> service.requestReset(new RequestPasswordResetCommand(
                "alice@example.com", "cid", "1234"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("executor saturated");

        verify(tokenStore).store(token.capture(), eq(userId), eq(18L), eq(Duration.ofSeconds(600)));
        verify(tokenStore).delete(token.getValue());
    }

    @Test
    void requestResetShouldConsumeTheSamePseudonymousQuotaForUnknownEmail() {
        properties.setRequestWindowSeconds(300);
        properties.setMaxRequestsPerEmail(1);
        properties.setMaxRequestsPerIp(20);
        when(resetRequestRateLimitRepository.increment(startsWith("auth:pwdreset:req:ip:"), eq(300)))
                .thenReturn(1);
        when(resetRequestRateLimitRepository.increment(startsWith("auth:pwdreset:req:email:"), eq(300)))
                .thenReturn(1);
        when(userCredentialQueryApi.findByEmailOrNull("alice@example.com")).thenReturn(null);

        PasswordResetRequestResult result = service.requestReset(new RequestPasswordResetCommand(
                " alice@example.com ",
                "cid",
                "1234",
                "203.0.113.10"
        ));

        assertThat(result.issued()).isTrue();
        ArgumentCaptor<String> rateKey = ArgumentCaptor.forClass(String.class);
        verify(resetRequestRateLimitRepository, org.mockito.Mockito.times(3))
                .increment(rateKey.capture(), eq(300));
        assertThat(rateKey.getAllValues())
                .allSatisfy(key -> assertThat(key).doesNotContain("alice@example.com").doesNotContain("203.0.113.10"));
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> deliveryId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> derivationKeyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> deliveryReference = ArgumentCaptor.forClass(String.class);
        verify(tokenStore).store(
                token.capture(),
                eq(new UUID(0L, 0L)),
                eq(0L),
                eq(Duration.ofSeconds(600))
        );
        verify(passwordResetMailDispatcher).dispatch(
                deliveryId.capture(),
                derivationKeyId.capture(),
                deliveryReference.capture(),
                eq(""),
                any(Instant.class));
        PasswordResetTokenDeriver.DeliveryMaterial delivery = tokenDeriver.deriveDelivery(deliveryId.getValue());
        assertThat(token.getValue()).isEqualTo(delivery.token());
        assertThat(derivationKeyId.getValue()).isEqualTo(delivery.derivationKeyId());
        assertThat(deliveryReference.getValue()).isEqualTo(delivery.deliveryReference());
    }

    @Test
    void requestResetShouldUseTheSameQuotaForDatabaseEquivalentOwnerEmails() {
        properties.setMaxRequestsPerIp(0);
        properties.setMaxRequestsPerEmail(3);
        when(resetRequestRateLimitRepository.increment(startsWith("auth:pwdreset:req:email:"), eq(3600)))
                .thenReturn(1);
        UUID userId = uuid(19);
        UserCredentialView user = credential(userId, "strasse@example.com", 23L);
        when(userCredentialQueryApi.findByEmailOrNull("straße@example.com")).thenReturn(user);
        when(userCredentialQueryApi.findByEmailOrNull("strasse@example.com")).thenReturn(user);

        service.requestReset(new RequestPasswordResetCommand("straße@example.com", "cid", "1234"));
        service.requestReset(new RequestPasswordResetCommand("STRASSE@example.com", "cid", "1234"));

        ArgumentCaptor<String> rateKey = ArgumentCaptor.forClass(String.class);
        verify(resetRequestRateLimitRepository, org.mockito.Mockito.times(4))
                .increment(rateKey.capture(), eq(3600));
        assertThat(rateKey.getAllValues()).hasSize(4);
        assertThat(rateKey.getAllValues().get(0)).isEqualTo(rateKey.getAllValues().get(2));
        assertThat(rateKey.getAllValues().get(1)).isEqualTo(rateKey.getAllValues().get(3));
        assertThat(rateKey.getAllValues().get(0)).isNotEqualTo(rateKey.getAllValues().get(1));
    }

    @Test
    void requestResetShouldExposeTheSameQuotaBoundaryForKnownAndUnknownEquivalentEmails() {
        properties.setMaxRequestsPerIp(0);
        properties.setMaxRequestsPerEmail(1);
        Map<String, Integer> counts = new HashMap<>();
        when(resetRequestRateLimitRepository.increment(anyString(), eq(3600)))
                .thenAnswer(invocation -> counts.merge(
                        invocation.getArgument(0, String.class),
                        1,
                        Integer::sum
                ));
        UserCredentialView user = credential(uuid(21), "strasse@example.com", 25L);
        when(userCredentialQueryApi.findByEmailOrNull("straße@example.com")).thenReturn(user);
        when(userCredentialQueryApi.findByEmailOrNull("strasse@example.com")).thenReturn(user);

        assertThat(service.requestReset(new RequestPasswordResetCommand(
                "straße@example.com", "cid", "1234"
        )).issued()).isTrue();
        assertTooManyRequests("STRASSE@example.com");

        assertThat(service.requestReset(new RequestPasswordResetCommand(
                "fuß@example.com", "cid", "1234"
        )).issued()).isTrue();
        assertTooManyRequests("FUSS@example.com");
    }

    @Test
    void requestResetShouldSilentlySuppressDeliveryWhenAliasesExceedAccountQuota() {
        properties.setMaxRequestsPerIp(0);
        properties.setMaxRequestsPerEmail(1);
        when(resetRequestRateLimitRepository.increment(
                startsWith("auth:pwdreset:req:email:"), eq(3600)
        )).thenReturn(1);
        when(resetRequestRateLimitRepository.increment(
                startsWith("auth:pwdreset:req:delivery:"), eq(3600)
        )).thenReturn(1, 2);
        UUID userId = uuid(22);
        UserCredentialView user = credential(userId, "owner@example.com", 26L);
        when(userCredentialQueryApi.findByEmailOrNull("alias-one@example.com")).thenReturn(user);
        when(userCredentialQueryApi.findByEmailOrNull("alias-two@example.com")).thenReturn(user);

        assertThat(service.requestReset(new RequestPasswordResetCommand(
                "alias-one@example.com", "cid", "1234"
        )).issued()).isTrue();
        assertThat(service.requestReset(new RequestPasswordResetCommand(
                "alias-two@example.com", "cid", "1234"
        )).issued()).isTrue();

        ArgumentCaptor<String> deliveryEmail = ArgumentCaptor.forClass(String.class);
        verify(passwordResetMailDispatcher, org.mockito.Mockito.times(2)).dispatch(
                any(UUID.class),
                anyString(),
                anyString(),
                deliveryEmail.capture(),
                any(Instant.class)
        );
        assertThat(deliveryEmail.getAllValues()).containsExactly("owner@example.com", "");
        verify(tokenStore).store(anyString(), eq(userId), eq(26L), eq(Duration.ofSeconds(600)));
        verify(tokenStore).store(
                anyString(), eq(new UUID(0L, 0L)), eq(0L), eq(Duration.ofSeconds(600))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://community.example",
            "//community.example",
            "https://user@community.example",
            "https://community.example/reset?campaign=mail",
            "https://community.example/reset#fragment",
            "https://community.example:0/reset",
            "https://community.example:65536/reset",
            "not a uri"
    })
    void requestResetShouldRejectUnsafeBaseUrlBeforeLookingUpUser(String baseUrl) {
        properties.setResetBaseUrl(baseUrl);

        assertThatThrownBy(() -> service.requestReset(new RequestPasswordResetCommand(
                "alice@example.com",
                "cid",
                "1234"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INTERNAL_ERROR);

        verifyNoInteractions(tokenStore, userCredentialQueryApi, passwordResetMailDispatcher);
    }

    @Test
    void requestResetShouldStopBeforeIssuingTokenWhenEmailQuotaIsExceeded() {
        properties.setRequestWindowSeconds(300);
        properties.setMaxRequestsPerEmail(1);
        properties.setMaxRequestsPerIp(20);
        when(userCredentialQueryApi.findByEmailOrNull("alice@example.com"))
                .thenReturn(credential(uuid(20), "alice@example.com", 24L));
        when(resetRequestRateLimitRepository.increment(startsWith("auth:pwdreset:req:ip:"), eq(300)))
                .thenReturn(1);
        when(resetRequestRateLimitRepository.increment(startsWith("auth:pwdreset:req:email:"), eq(300)))
                .thenReturn(2);

        assertThatThrownBy(() -> service.requestReset(new RequestPasswordResetCommand(
                "alice@example.com",
                "cid",
                "1234",
                "203.0.113.10"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);

        verify(userCredentialQueryApi).findByEmailOrNull("alice@example.com");
        verifyNoInteractions(tokenStore, passwordResetMailDispatcher);
    }

    @Test
    void confirmResetShouldCasVersionThenRevokeGenerationAndFinishOwnedLease(CapturedOutput output) {
        UUID userId = uuid(9);
        PasswordResetTokenRepository.PendingPasswordResetToken pending = pending(userId, 19L);
        when(tokenStore.beginConfirmation(eq("token-123"), any(Instant.class), any(UUID.class)))
                .thenReturn(pending);
        when(userCredentialActionApi.updatePasswordIfSecurityVersion(userId, "New-password-1", 19L))
                .thenReturn(true);
        when(tokenStore.finishConfirmation("token-123", userId, 19L, LEASE_ID)).thenReturn(true);

        assertThat(service.confirmReset(new ConfirmPasswordResetCommand(
                " token-123 ",
                "New-password-1",
                "cid",
                "1234"
        ))).isTrue();

        verify(tokenStore).revokeGeneration(userId, 19L, Duration.ofSeconds(600));
        verify(tokenStore).finishConfirmation("token-123", userId, 19L, LEASE_ID);
        assertThat(output.getAll()).doesNotContain("token-123").doesNotContain("New-password-1");
    }

    @Test
    void confirmResetShouldRejectStaleSiblingAndOnlyRevokeItsGeneration() {
        UUID userId = uuid(10);
        PasswordResetTokenRepository.PendingPasswordResetToken pending = pending(userId, 20L);
        when(tokenStore.beginConfirmation(eq("stale-token"), any(Instant.class), any(UUID.class)))
                .thenReturn(pending);
        when(userCredentialActionApi.updatePasswordIfSecurityVersion(userId, "New-password-2", 20L))
                .thenReturn(false);
        when(tokenStore.finishConfirmation("stale-token", userId, 20L, LEASE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.confirmReset(new ConfirmPasswordResetCommand(
                "stale-token",
                "New-password-2",
                "cid",
                "1234"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.PASSWORD_RESET_INVALID);

        verify(tokenStore).revokeGeneration(userId, 20L, Duration.ofSeconds(600));
        verify(tokenStore).finishConfirmation("stale-token", userId, 20L, LEASE_ID);
        verify(tokenStore, never()).rollbackConfirmation(anyString(), any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void confirmResetShouldRollbackOwnedLeaseWhenCredentialStoreFails() {
        UUID userId = uuid(11);
        RuntimeException updateFailure = new IllegalStateException("credential store down");
        PasswordResetTokenRepository.PendingPasswordResetToken pending = pending(userId, 21L);
        when(tokenStore.beginConfirmation(eq("token-123"), any(Instant.class), any(UUID.class)))
                .thenReturn(pending);
        when(userCredentialActionApi.updatePasswordIfSecurityVersion(userId, "New-password-3", 21L))
                .thenThrow(updateFailure);
        when(tokenStore.rollbackConfirmation("token-123", userId, 21L, LEASE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.confirmReset(new ConfirmPasswordResetCommand(
                "token-123",
                "New-password-3",
                "cid",
                "1234"
        ))).isSameAs(updateFailure);

        verify(tokenStore).rollbackConfirmation("token-123", userId, 21L, LEASE_ID);
        verify(tokenStore, never()).revokeGeneration(any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void confirmResetShouldKeepOriginalFailureWhenLeaseRollbackAlsoFails() {
        UUID userId = uuid(12);
        RuntimeException updateFailure = new IllegalStateException("credential store down");
        RuntimeException rollbackFailure = new IllegalStateException("redis down");
        PasswordResetTokenRepository.PendingPasswordResetToken pending = pending(userId, 22L);
        when(tokenStore.beginConfirmation(eq("token-123"), any(Instant.class), any(UUID.class)))
                .thenReturn(pending);
        when(userCredentialActionApi.updatePasswordIfSecurityVersion(userId, "New-password-4", 22L))
                .thenThrow(updateFailure);
        when(tokenStore.rollbackConfirmation("token-123", userId, 22L, LEASE_ID))
                .thenThrow(rollbackFailure);

        assertThatThrownBy(() -> service.confirmReset(new ConfirmPasswordResetCommand(
                "token-123",
                "New-password-4",
                "cid",
                "1234"
        )))
                .isSameAs(updateFailure)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(rollbackFailure));
    }

    @Test
    void confirmResetShouldValidatePasswordBeforeAcquiringTokenLease() {
        BusinessException weakPassword = new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "weak password");
        doThrow(weakPassword).when(userCredentialActionApi).validatePasswordPolicy("weakpass");

        assertThatThrownBy(() -> service.confirmReset(new ConfirmPasswordResetCommand(
                "token-123",
                "weakpass",
                "cid",
                "1234"
        ))).isSameAs(weakPassword);

        verify(tokenStore, never()).beginConfirmation(anyString(), any(), any());
    }

    @Test
    void confirmResetShouldRejectInvalidTokenWithoutLoggingSecrets(CapturedOutput output) {
        when(tokenStore.beginConfirmation(eq("token-123"), any(Instant.class), any(UUID.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.confirmReset(new ConfirmPasswordResetCommand(
                "token-123",
                "New-password-5",
                "cid",
                "1234"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.PASSWORD_RESET_INVALID);

        assertThat(output.getAll()).doesNotContain("token-123").doesNotContain("New-password-5");
    }

    private static UserCredentialView credential(UUID userId, String email, long securityVersion) {
        return new UserCredentialView(userId, "alice", email, 1, 0, null, securityVersion, true, true);
    }

    private static PasswordResetTokenRepository.PendingPasswordResetToken pending(UUID userId, long version) {
        return new PasswordResetTokenRepository.PendingPasswordResetToken(userId, version, LEASE_ID);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private void assertTooManyRequests(String email) {
        assertThatThrownBy(() -> service.requestReset(new RequestPasswordResetCommand(
                email, "cid", "1234"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
    }
}
