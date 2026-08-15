package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.config.CaptchaProperties;
import com.nowcoder.community.auth.domain.repository.CaptchaRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptchaApplicationServiceTest {

    private static final String CAPTCHA_ID = "0123456789abcdef0123456789abcdef";

    @Mock
    private CaptchaRepository captchaStore;

    private CaptchaApplicationService service;

    @BeforeEach
    void setUp() {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setTtlSeconds(60);
        properties.setMaxFailures(3);
        properties.setMaxIssueRequestsPerIp(1);
        service = new CaptchaApplicationService(properties, captchaStore);
    }

    @Test
    void verifyShouldReturnTrueWhenCaptchaMatchesAtomically() {
        when(captchaStore.verifyAndConsume(CAPTCHA_ID, "AbC1", 3, Duration.ofSeconds(60)))
                .thenReturn(CaptchaRepository.VerifyResult.MATCHED);

        assertThat(service.verify(CAPTCHA_ID, "  AbC1  ")).isTrue();

        verify(captchaStore).verifyAndConsume(CAPTCHA_ID, "AbC1", 3, Duration.ofSeconds(60));
        verify(captchaStore, never()).incrementFailures(anyString(), any(Duration.class));
        verify(captchaStore, never()).delete(anyString());
    }

    @Test
    void verifyShouldReturnFalseWhenCaptchaNotFound() {
        when(captchaStore.verifyAndConsume(CAPTCHA_ID, "AbC1", 3, Duration.ofSeconds(60)))
                .thenReturn(CaptchaRepository.VerifyResult.NOT_FOUND);

        assertThat(service.verify(CAPTCHA_ID, "AbC1")).isFalse();

        verify(captchaStore, never()).incrementFailures(anyString(), any(Duration.class));
        verify(captchaStore, never()).delete(anyString());
    }

    @Test
    void verifyShouldLeaveFailureExhaustionToTheAtomicRepositoryOperation() {
        when(captchaStore.verifyAndConsume(CAPTCHA_ID, "wrong", 3, Duration.ofSeconds(60)))
                .thenReturn(CaptchaRepository.VerifyResult.EXHAUSTED);

        assertThat(service.verify(CAPTCHA_ID, "wrong")).isFalse();

        verify(captchaStore, never()).incrementFailures(eq(CAPTCHA_ID), any(Duration.class));
        verify(captchaStore, never()).delete(CAPTCHA_ID);
    }

    @Test
    void verifyShouldFailClosedWhenStoreUnavailable() {
        when(captchaStore.verifyAndConsume(CAPTCHA_ID, "code", 3, Duration.ofSeconds(60)))
                .thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.verify(CAPTCHA_ID, "code"))
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

        assertThatThrownBy(() -> service.issue(new CaptchaApplicationService.IssueCaptchaCommand("127.0.0.1")))
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

    @Test
    void verifyShouldRejectMalformedOrHashTagBreakingCaptchaIdBeforeRedis() {
        assertThat(service.verify("}x", "AbC1")).isFalse();
        assertThat(service.verify("0123456789abcdef0123456789abcdeG", "AbC1")).isFalse();
        assertThat(service.verify(" " + CAPTCHA_ID, "AbC1")).isFalse();

        verify(captchaStore, never()).verifyAndConsume(anyString(), anyString(), anyInt(), any(Duration.class));
    }
}
