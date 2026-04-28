package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptchaDomainServiceTest {

    private final CaptchaDomainService service = new CaptchaDomainService();

    @Test
    void requireCaptchaShouldRejectMissingFields() {
        assertThatThrownBy(() -> service.requireCaptcha("cid", ""))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.CAPTCHA_REQUIRED);
    }

    @Test
    void normalizeCodeShouldTrimAndReturnEmptyForMissingCode() {
        assertThat(service.normalizeCode("  AbC1  ")).isEqualTo("AbC1");
        assertThat(service.normalizeCode(null)).isEmpty();
    }
}
