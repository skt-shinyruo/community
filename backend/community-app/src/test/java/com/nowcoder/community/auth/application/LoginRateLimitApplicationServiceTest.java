package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import com.nowcoder.community.auth.domain.service.LoginRateLimitDomainService;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginRateLimitApplicationServiceTest {

    private final LoginRateLimitRepository loginRateLimitRepository = mock(LoginRateLimitRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);

    private LoginRateLimitApplicationService service;

    @BeforeEach
    void setUp() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties();
        service = new LoginRateLimitApplicationService(
                properties,
                loginRateLimitRepository,
                new LoginRateLimitDomainService(),
                meterRegistryProvider
        );
    }

    @Test
    void assertNotBlockedShouldFailClosedWhenRepositoryReadThrows() {
        when(loginRateLimitRepository.count(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.assertNotBlocked("alice", "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void isCaptchaRequiredShouldReturnTrueWhenRepositoryReadThrows() {
        when(loginRateLimitRepository.count(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.isCaptchaRequired("alice", "127.0.0.1")).isTrue();
    }

    @Test
    void recordFailureShouldDelegateIncrementWithNormalizedKeyAndWindow() {
        when(loginRateLimitRepository.increment("auth:login:fail:ip:127.0.0.1", 60)).thenReturn(1);

        service.recordFailure(null, "127.0.0.1", "remote");

        verify(loginRateLimitRepository).increment("auth:login:fail:ip:127.0.0.1", 60);
    }

    @Test
    void recordFailureShouldFailClosedWhenRepositoryIncrementThrows() {
        when(loginRateLimitRepository.increment("auth:login:fail:ip:127.0.0.1", 60))
                .thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.recordFailure(null, "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}
