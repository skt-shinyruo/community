package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationDomainServiceTest {

    private final RegistrationDomainService service = new RegistrationDomainService();

    @Test
    void requireRegisterFieldsShouldRejectMissingEmail() {
        assertThatThrownBy(() -> service.requireRegisterFields("alice", "secret", ""))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void maskEmailShouldHideLocalPart() {
        assertThat(service.maskEmail("alice@example.com")).isEqualTo("a***e@example.com");
        assertThat(service.maskEmail("a@example.com")).isEqualTo("*@example.com");
    }
}
