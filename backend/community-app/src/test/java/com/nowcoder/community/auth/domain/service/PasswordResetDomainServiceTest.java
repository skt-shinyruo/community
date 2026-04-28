package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class PasswordResetDomainServiceTest {

    private final PasswordResetDomainService service = new PasswordResetDomainService();

    @Test
    void requireResetRequestEmailShouldRejectBlankEmail() {
        assertThatThrownBy(() -> service.requireResetRequestEmail(""))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void requireConfirmFieldsShouldAcceptPresentFields() {
        assertThatCode(() -> service.requireConfirmFields("token", "new-password")).doesNotThrowAnyException();
    }
}
