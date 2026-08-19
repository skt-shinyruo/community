package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class LoginRateLimitApplicationServiceTest {

    private static final String IP_FAILURE_KEY =
            "auth:login:fail:ip:v2-9MUtFteax4E6B3rr8Wr50Od_KVnBQBmQW5oJ9lTlSCc";
    private static final String AUTHENTICATION_SUBJECT = "utf8mb4_unicode_ci:v1:abc123";

    private final LoginRateLimitRepository loginRateLimitRepository = mock(LoginRateLimitRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);

    private LoginRateLimitApplicationService service;
    private String inputFailureKey;
    private String subjectFailureKey;

    @BeforeEach
    void setUp() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties();
        PasswordResetProperties passwordResetProperties = new PasswordResetProperties();
        passwordResetProperties.setQuotaHmacSecret(
                "login-rate-limit-test-hmac-secret-at-least-32-bytes");
        service = new LoginRateLimitApplicationService(
                properties,
                loginRateLimitRepository,
                new PasswordResetTokenDeriver(passwordResetProperties),
                meterRegistryProvider,
                mock(ScheduledExecutorService.class)
        );
        inputFailureKey = "auth:login:fail:input:v3-"
                + identifierDeriver().identifierId("login-input", "alice");
        subjectFailureKey = "auth:login:fail:subject:v3-"
                + identifierDeriver().identifierId("login-subject", AUTHENTICATION_SUBJECT);
    }

    @Test
    void lookupReservationShouldFailClosedWhenRepositoryThrows() {
        when(loginRateLimitRepository.tryAcquire(anyString(), anyString(),
                any(UUID.class), eq(20), eq(120_000)))
                .thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.acquirePasswordCheck("alice", "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void isCaptchaRequiredShouldReturnTrueWhenRepositoryReadThrows() {
        when(loginRateLimitRepository.count(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.isCaptchaRequired("alice", "127.0.0.1", null)).isTrue();
    }

    @Test
    void recordFailureShouldDelegateIncrementWithNormalizedKeyAndWindow() {
        when(loginRateLimitRepository.increment(IP_FAILURE_KEY, 60)).thenReturn(1);

        service.recordFailure(null, "127.0.0.1", "remote");

        verify(loginRateLimitRepository).increment(IP_FAILURE_KEY, 60);
    }

    @Test
    void recordFailureShouldFailClosedWhenRepositoryIncrementThrows() {
        when(loginRateLimitRepository.increment(IP_FAILURE_KEY, 60))
                .thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.recordFailure(null, "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void recordFailureShouldCommitBothDimensionsBeforeReportingTheThreshold() {
        when(loginRateLimitRepository.increment(IP_FAILURE_KEY, 60)).thenReturn(20);
        when(loginRateLimitRepository.increment(subjectFailureKey, 60)).thenReturn(1);

        assertThatThrownBy(() -> service.recordFailure(AUTHENTICATION_SUBJECT, "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);

        var ordered = inOrder(loginRateLimitRepository);
        ordered.verify(loginRateLimitRepository).increment(IP_FAILURE_KEY, 60);
        ordered.verify(loginRateLimitRepository).increment(subjectFailureKey, 60);
    }

    @Test
    void resetSubjectShouldDeleteOnlyTheAuthoritativeSubjectBucket() {
        service.resetSubject(" " + AUTHENTICATION_SUBJECT + " ");

        verify(loginRateLimitRepository).delete(subjectFailureKey);
        verifyNoMoreInteractions(loginRateLimitRepository);
    }

    @Test
    void acquirePasswordCheckShouldReserveBothDimensionsAndReleaseInReverseOrder() {
        String ipFailureKey = IP_FAILURE_KEY;
        String ipLeaseKey = "auth:login:inflight:{" + ipFailureKey + "}:" + ipFailureKey;
        String userFailureKey = inputFailureKey;
        String userLeaseKey = "auth:login:inflight:{" + userFailureKey + "}:" + userFailureKey;
        when(loginRateLimitRepository.tryAcquire(eq(ipFailureKey), eq(ipLeaseKey),
                any(UUID.class), eq(20), eq(120_000)))
                .thenReturn(true);
        when(loginRateLimitRepository.tryAcquire(eq(userFailureKey), eq(userLeaseKey),
                any(UUID.class), eq(5), eq(120_000)))
                .thenReturn(true);

        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                service.acquirePasswordCheck("alice", "127.0.0.1", "remote");
        service.releasePasswordCheck(permit);

        var ordered = inOrder(loginRateLimitRepository);
        ordered.verify(loginRateLimitRepository)
                .tryAcquire(eq(ipFailureKey), eq(ipLeaseKey),
                        eq(permit.token()), eq(20), eq(120_000));
        ordered.verify(loginRateLimitRepository)
                .tryAcquire(eq(userFailureKey), eq(userLeaseKey),
                        eq(permit.token()), eq(5), eq(120_000));
        ordered.verify(loginRateLimitRepository)
                .release(userLeaseKey, permit.token());
        ordered.verify(loginRateLimitRepository)
                .release(ipLeaseKey, permit.token());
    }

    @Test
    void acquirePasswordCheckShouldReleasePartialPermitWhenSecondDimensionIsFull() {
        String ipFailureKey = IP_FAILURE_KEY;
        String ipLeaseKey = "auth:login:inflight:{" + ipFailureKey + "}:" + ipFailureKey;
        String userFailureKey = inputFailureKey;
        String userLeaseKey = "auth:login:inflight:{" + userFailureKey + "}:" + userFailureKey;
        when(loginRateLimitRepository.tryAcquire(eq(ipFailureKey), eq(ipLeaseKey),
                any(UUID.class), eq(20), eq(120_000)))
                .thenReturn(true);
        when(loginRateLimitRepository.tryAcquire(eq(userFailureKey), eq(userLeaseKey),
                any(UUID.class), eq(5), eq(120_000)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.acquirePasswordCheck("alice", "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);

        verify(loginRateLimitRepository).release(
                eq(ipLeaseKey), any(UUID.class));
    }

    @Test
    void acquirePasswordCheckShouldEncodeBracesBeforeBuildingTheRedisHashTag() {
        when(loginRateLimitRepository.tryAcquire(
                org.mockito.ArgumentMatchers.startsWith("auth:login:fail:input:v3-"),
                anyString(),
                any(UUID.class), eq(5), eq(120_000)))
                .thenReturn(true);

        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                service.acquirePasswordCheck("A{B}", null, "remote");
        service.releasePasswordCheck(permit);

        verify(loginRateLimitRepository).release(
                org.mockito.ArgumentMatchers.startsWith("auth:login:inflight:{auth:login:fail:input:v3-"),
                eq(permit.token()));
    }

    @Test
    void untrustedKeyComponentsShouldBeOpaqueVersionedAndCollisionResistant() {
        when(loginRateLimitRepository.tryAcquire(anyString(), anyString(),
                any(UUID.class), eq(5), eq(120_000)))
                .thenReturn(true);
        ArgumentCaptor<String> failureKeys = ArgumentCaptor.forClass(String.class);

        LoginRateLimitApplicationService.PasswordCheckPermit encodedInput =
                service.acquirePasswordCheck("{}", null, "remote");
        service.releasePasswordCheck(encodedInput);
        LoginRateLimitApplicationService.PasswordCheckPermit legacyLookingInput =
                service.acquirePasswordCheck("b64-e30", null, "remote");
        service.releasePasswordCheck(legacyLookingInput);

        verify(loginRateLimitRepository, times(2)).tryAcquire(
                failureKeys.capture(), anyString(), any(UUID.class), eq(5), eq(120_000));
        List<String> captured = failureKeys.getAllValues();
        assertThat(captured).hasSize(2).doesNotHaveDuplicates();
        assertThat(captured).allSatisfy(key -> {
            assertThat(key).startsWith("auth:login:fail:input:v3-");
            assertThat(key).doesNotContain("{").doesNotContain("}").doesNotContain("b64-e30");
        });
    }

    @Test
    void rateLimitKeysShouldNotExposeNormalizedUsernameOrIp() {
        when(loginRateLimitRepository.tryAcquire(anyString(), anyString(),
                any(UUID.class), any(Integer.class), eq(120_000)))
                .thenReturn(true);
        ArgumentCaptor<String> failureKeys = ArgumentCaptor.forClass(String.class);

        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                service.acquirePasswordCheck("Alice", "127.0.0.1", "remote");

        verify(loginRateLimitRepository, times(2)).tryAcquire(
                failureKeys.capture(), anyString(), eq(permit.token()),
                any(Integer.class), eq(120_000));
        assertThat(failureKeys.getAllValues())
                .allSatisfy(key -> assertThat(key)
                        .matches(".*:v[23]-.*")
                        .doesNotContain("alice")
                        .doesNotContain("127.0.0.1")
                        .doesNotContain("{")
                        .doesNotContain("}"));
        service.releasePasswordCheck(permit);
    }

    @Test
    void authoritativeSubjectShouldBeAcquiredBeforeTheProvisionalLeaseIsReleased() {
        when(loginRateLimitRepository.tryAcquire(anyString(), anyString(),
                any(UUID.class), any(Integer.class), eq(120_000)))
                .thenReturn(true);
        ArgumentCaptor<String> initialLeaseKeys = ArgumentCaptor.forClass(String.class);
        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                service.acquirePasswordCheck("Alice", "127.0.0.1", "remote");
        verify(loginRateLimitRepository, times(2)).tryAcquire(
                anyString(), initialLeaseKeys.capture(), eq(permit.token()),
                any(Integer.class), eq(120_000));
        String provisionalLeaseKey = initialLeaseKeys.getAllValues().stream()
                .filter(key -> key.contains("auth:login:fail:input:"))
                .findFirst()
                .orElseThrow();
        clearInvocations(loginRateLimitRepository);
        when(loginRateLimitRepository.renew(anyString(), eq(permit.token()), eq(120_000)))
                .thenReturn(true);

        service.attachAuthenticationSubject(
                permit,
                "Alice",
                "utf8mb4_unicode_ci:v1:abc123",
                "remote"
        );

        ArgumentCaptor<String> subjectFailureKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectLeaseKey = ArgumentCaptor.forClass(String.class);
        var order = inOrder(loginRateLimitRepository);
        order.verify(loginRateLimitRepository, times(2))
                .renew(anyString(), eq(permit.token()), eq(120_000));
        order.verify(loginRateLimitRepository).tryAcquire(
                subjectFailureKey.capture(), subjectLeaseKey.capture(),
                eq(permit.token()), eq(5), eq(120_000));
        order.verify(loginRateLimitRepository).release(provisionalLeaseKey, permit.token());
        assertThat(subjectFailureKey.getValue()).startsWith("auth:login:fail:subject:v3-");
        assertThat(permit.keys())
                .contains(subjectLeaseKey.getValue())
                .doesNotContain(provisionalLeaseKey);
    }

    @Test
    void authoritativeSubjectCapacityFailureShouldKeepTheProvisionalPermitForFinallyRelease() {
        when(loginRateLimitRepository.tryAcquire(anyString(), anyString(),
                any(UUID.class), any(Integer.class), eq(120_000)))
                .thenReturn(true);
        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                service.acquirePasswordCheck("Alice", "127.0.0.1", "remote");
        List<String> provisionalKeys = permit.keys();
        clearInvocations(loginRateLimitRepository);
        when(loginRateLimitRepository.renew(anyString(), eq(permit.token()), eq(120_000)))
                .thenReturn(true);
        when(loginRateLimitRepository.tryAcquire(
                org.mockito.ArgumentMatchers.startsWith("auth:login:fail:subject:v3-"),
                anyString(), eq(permit.token()), eq(5), eq(120_000)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.attachAuthenticationSubject(
                permit, "Alice", "utf8mb4_unicode_ci:v1:abc123", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);

        assertThat(permit.keys()).containsExactlyElementsOf(provisionalKeys);
        verify(loginRateLimitRepository, never()).release(anyString(), eq(permit.token()));
    }

    @Test
    void resolvedIdentityShouldFailClosedWhenTheLookupLeaseWasLost() {
        when(loginRateLimitRepository.tryAcquire(anyString(), anyString(),
                any(UUID.class), any(Integer.class), eq(120_000)))
                .thenReturn(true);
        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                service.acquirePasswordCheck("Alice", "127.0.0.1", "remote");
        clearInvocations(loginRateLimitRepository);
        when(loginRateLimitRepository.renew(anyString(), eq(permit.token()), eq(120_000)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.attachAuthenticationSubject(
                permit, "Alice", AUTHENTICATION_SUBJECT, "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);

        verify(loginRateLimitRepository, never()).tryAcquire(
                anyString(), anyString(), eq(permit.token()), any(Integer.class), eq(120_000));
    }

    @Test
    void captchaDecisionShouldIncludeTheCallersLiveReservation() {
        String leaseKey = "auth:login:inflight:{" + subjectFailureKey + "}:" + subjectFailureKey;
        when(loginRateLimitRepository.tryAcquire(anyString(), anyString(),
                any(UUID.class), eq(5), eq(120_000))).thenReturn(true);
        when(loginRateLimitRepository.renew(anyString(), any(UUID.class), eq(120_000)))
                .thenReturn(true);
        when(loginRateLimitRepository.countBudget(subjectFailureKey, leaseKey)).thenReturn(2);

        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                service.acquirePasswordCheck("alice", null, "remote");
        service.attachAuthenticationSubject(
                permit, "alice", AUTHENTICATION_SUBJECT, "remote");

        assertThat(service.isCaptchaRequired(AUTHENTICATION_SUBJECT, null, permit)).isTrue();
        verify(loginRateLimitRepository).countBudget(subjectFailureKey, leaseKey);
        service.releasePasswordCheck(permit);
    }

    @Test
    void passwordCheckPermitShouldRenewOwnedSlotsUntilReleased() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> renewal = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        doReturn(renewal).when(scheduler).scheduleWithFixedDelay(
                heartbeat.capture(), eq(30_000L), eq(30_000L), eq(TimeUnit.MILLISECONDS));
        LoginRateLimitApplicationService renewingService = new LoginRateLimitApplicationService(
                new LoginRateLimitProperties(),
                loginRateLimitRepository,
                identifierDeriver(),
                meterRegistryProvider,
                scheduler
        );
        String failureKey = inputFailureKey;
        String leaseKey = "auth:login:inflight:{" + failureKey + "}:" + failureKey;
        when(loginRateLimitRepository.tryAcquire(eq(failureKey), eq(leaseKey),
                any(UUID.class), eq(5), eq(120_000))).thenReturn(true);
        when(loginRateLimitRepository.renew(eq(leaseKey), any(UUID.class), eq(120_000)))
                .thenReturn(true);

        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                renewingService.acquirePasswordCheck("alice", null, "remote");
        heartbeat.getValue().run();

        renewingService.assertPasswordCheckOwned(permit);
        verify(loginRateLimitRepository, times(2)).renew(leaseKey, permit.token(), 120_000);

        renewingService.releasePasswordCheck(permit);
        verify(renewal).cancel(false);

        heartbeat.getValue().run();
        renewingService.releasePasswordCheck(permit);
        verify(loginRateLimitRepository, times(2)).renew(leaseKey, permit.token(), 120_000);
        verify(loginRateLimitRepository, times(1)).release(leaseKey, permit.token());
        verify(renewal, times(1)).cancel(false);
    }

    @Test
    void closeShouldWaitForInFlightRenewalAndPreventLaterCallbacks() throws Exception {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> renewal = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        doReturn(renewal).when(scheduler).scheduleWithFixedDelay(
                heartbeat.capture(), eq(30_000L), eq(30_000L), eq(TimeUnit.MILLISECONDS));
        LoginRateLimitApplicationService renewingService = new LoginRateLimitApplicationService(
                new LoginRateLimitProperties(), loginRateLimitRepository, identifierDeriver(),
                meterRegistryProvider, scheduler);
        String failureKey = inputFailureKey;
        String leaseKey = "auth:login:inflight:{" + failureKey + "}:" + failureKey;
        when(loginRateLimitRepository.tryAcquire(eq(failureKey), eq(leaseKey),
                any(UUID.class), eq(5), eq(120_000))).thenReturn(true);
        CountDownLatch renewalEntered = new CountDownLatch(1);
        CountDownLatch allowRenewal = new CountDownLatch(1);
        when(loginRateLimitRepository.renew(eq(leaseKey), any(UUID.class), eq(120_000)))
                .thenAnswer(invocation -> {
                    renewalEntered.countDown();
                    return allowRenewal.await(5, TimeUnit.SECONDS);
                });
        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                renewingService.acquirePasswordCheck("alice", null, "remote");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var renewing = executor.submit(heartbeat.getValue());
            assertThat(renewalEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var closing = executor.submit(() -> renewingService.releasePasswordCheck(permit));
            allowRenewal.countDown();
            renewing.get(5, TimeUnit.SECONDS);
            closing.get(5, TimeUnit.SECONDS);

            heartbeat.getValue().run();
            renewingService.releasePasswordCheck(permit);
            verify(loginRateLimitRepository, times(1)).renew(leaseKey, permit.token(), 120_000);
            verify(loginRateLimitRepository, times(1)).release(leaseKey, permit.token());
            verify(renewal, times(1)).cancel(false);
        } finally {
            executor.shutdownNow();
        }
    }

    private PasswordResetTokenDeriver identifierDeriver() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setQuotaHmacSecret("login-rate-limit-test-hmac-secret-at-least-32-bytes");
        return new PasswordResetTokenDeriver(properties);
    }
}
