package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.config.CaptchaProperties;
import com.nowcoder.community.auth.domain.repository.CaptchaRepository;
import com.nowcoder.community.auth.domain.service.CaptchaDomainService;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptchaApplicationServiceTest {

    @Mock
    private CaptchaRepository captchaStore;

    private CaptchaApplicationService service;

    @BeforeEach
    void setUp() {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setTtlSeconds(60);
        properties.setMaxFailures(3);
        properties.setMaxIssueRequestsPerIp(1);
        service = new CaptchaApplicationService(properties, captchaStore, new CaptchaDomainService());
    }

    @Test
    void verifyShouldReturnTrueWhenCaptchaMatchesAtomically() {
        when(captchaStore.verifyAndConsume("cid", "AbC1")).thenReturn(CaptchaRepository.VerifyResult.MATCHED);

        assertThat(service.verify("cid", "  AbC1  ")).isTrue();

        verify(captchaStore).verifyAndConsume("cid", "AbC1");
        verify(captchaStore, never()).incrementFailures(anyString(), any(Duration.class));
        verify(captchaStore, never()).delete(anyString());
    }

    @Test
    void verifyShouldReturnFalseWhenCaptchaNotFound() {
        when(captchaStore.verifyAndConsume("cid", "AbC1")).thenReturn(CaptchaRepository.VerifyResult.NOT_FOUND);

        assertThat(service.verify("cid", "AbC1")).isFalse();

        verify(captchaStore, never()).incrementFailures(anyString(), any(Duration.class));
        verify(captchaStore, never()).delete(anyString());
    }

    @Test
    void verifyShouldDeleteCaptchaAfterTooManyFailures() {
        when(captchaStore.verifyAndConsume("cid", "wrong")).thenReturn(CaptchaRepository.VerifyResult.MISMATCH);
        when(captchaStore.incrementFailures("cid", Duration.ofSeconds(60))).thenReturn(3);

        assertThat(service.verify("cid", "wrong")).isFalse();

        verify(captchaStore).delete("cid");
    }

    @Test
    void verifyShouldFailClosedWhenStoreUnavailable() {
        when(captchaStore.verifyAndConsume("cid", "code")).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.verify("cid", "code"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void issueShouldFailClosedWhenStoreUnavailable() {
        doThrow(new RuntimeException("redis down")).when(captchaStore).save(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> service.issue())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void issueShouldRateLimitByClientIp() {
        when(captchaStore.incrementFailures("auth:captcha:issue:ip:127.0.0.1", Duration.ofSeconds(60))).thenReturn(2);

        assertThatThrownBy(() -> service.issue(new com.nowcoder.community.auth.application.command.IssueCaptchaCommand("127.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void issueShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.issue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }
}
