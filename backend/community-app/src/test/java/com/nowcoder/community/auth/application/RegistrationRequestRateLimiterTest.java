package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.application.port.RegistrationRateLimitPort;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrationRequestRateLimiterTest {

    private final RegistrationRateLimitPort rateLimitPort = mock(RegistrationRateLimitPort.class);
    private RegistrationRequestRateLimiter limiter;

    @BeforeEach
    void setUp() {
        RegistrationProperties registration = new RegistrationProperties();
        PasswordResetProperties passwordReset = new PasswordResetProperties();
        passwordReset.setIdentifierHmacSecret("registration-quota-test-secret-at-least-32-bytes");
        limiter = new RegistrationRequestRateLimiter(
                registration,
                rateLimitPort,
                new PasswordResetTokenDeriver(passwordReset)
        );
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void enforceShouldUseOneAtomicRequestWithOpaqueStableCanonicalIdentities() {
        when(rateLimitPort.tryConsume(any(), any(), anyList())).thenReturn(true);

        limiter.enforce("JOSÉ", "Alice@EXAMPLE.COM", " 127.0.0.1 ");
        limiter.enforce("jose", "alice@example.com", "127.0.0.1");

        ArgumentCaptor<List<RegistrationRateLimitPort.Quota>> quotas =
                (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(rateLimitPort, times(2)).tryConsume(
                eq(RegistrationRateLimitPort.Flow.REQUEST),
                eq(Duration.ofHours(1)),
                quotas.capture()
        );
        assertThat(quotas.getAllValues().get(0)).containsExactlyElementsOf(quotas.getAllValues().get(1));
        assertThat(quotas.getValue())
                .extracting(RegistrationRateLimitPort.Quota::dimension)
                .containsExactly(
                        RegistrationRateLimitPort.Dimension.IP,
                        RegistrationRateLimitPort.Dimension.USERNAME,
                        RegistrationRateLimitPort.Dimension.EMAIL
                );
        assertThat(quotas.getValue()).allSatisfy(quota -> assertThat(quota.opaqueIdentifier())
                .doesNotContain("alice@example.com")
                .doesNotContain("127.0.0.1")
                .matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void enforceShouldReturnOneGenericLimitErrorForAnyExhaustedDimension() {
        when(rateLimitPort.tryConsume(any(), any(), anyList())).thenReturn(false);

        assertThatThrownBy(() -> limiter.enforce("alice", "alice@example.com", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void enforceShouldFailClosedWhenRedisIsUnavailable() {
        when(rateLimitPort.tryConsume(any(), any(), anyList())).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> limiter.enforce("alice", "alice@example.com", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void enforceResendShouldConsumeIpEmailAndRegistrationBucketsTogether() {
        when(rateLimitPort.tryConsume(any(), any(), anyList())).thenReturn(true);
        UUID registrationId = UUID.fromString("00000000-0000-7000-8000-000000000007");

        limiter.enforceResend(registrationId, "Alice@EXAMPLE.COM", "127.0.0.1");

        ArgumentCaptor<List<RegistrationRateLimitPort.Quota>> quotas =
                (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(rateLimitPort).tryConsume(
                eq(RegistrationRateLimitPort.Flow.RESEND),
                eq(Duration.ofHours(1)),
                quotas.capture()
        );
        assertThat(quotas.getValue())
                .extracting(
                        RegistrationRateLimitPort.Quota::dimension,
                        RegistrationRateLimitPort.Quota::maximum
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(RegistrationRateLimitPort.Dimension.IP, 20),
                        org.assertj.core.groups.Tuple.tuple(RegistrationRateLimitPort.Dimension.EMAIL, 5),
                        org.assertj.core.groups.Tuple.tuple(RegistrationRateLimitPort.Dimension.REGISTRATION, 5)
                );
        assertThat(quotas.getValue()).allSatisfy(quota -> assertThat(quota.opaqueIdentifier())
                .doesNotContain("alice@example.com")
                .doesNotContain(registrationId.toString())
                .doesNotContain("127.0.0.1"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void unresolvedClientIpShouldStillConsumeOneStableIpBucket() {
        when(rateLimitPort.tryConsume(any(), any(), anyList())).thenReturn(true);

        limiter.enforce("alice", "alice@example.com", null);
        limiter.enforce("alice", "alice@example.com", "  ");

        ArgumentCaptor<List<RegistrationRateLimitPort.Quota>> quotas =
                (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(rateLimitPort, times(2)).tryConsume(
                eq(RegistrationRateLimitPort.Flow.REQUEST),
                eq(Duration.ofHours(1)),
                quotas.capture()
        );
        assertThat(quotas.getAllValues()).allSatisfy(value -> assertThat(value).hasSize(3));
        assertThat(quotas.getAllValues().get(0).get(0))
                .isEqualTo(quotas.getAllValues().get(1).get(0));
    }

    @Test
    void resendWithoutAStableRegistrationIdentityShouldFailClosed() {
        assertThatThrownBy(() -> limiter.enforceResend(null, "alice@example.com", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        verifyNoInteractions(rateLimitPort);
    }
}
