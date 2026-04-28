package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class AuthDomainServiceTest {

    private final AuthDomainService service = new AuthDomainService();

    @Test
    void requireCredentialsShouldRejectBlankUsernameOrPassword() {
        assertThatThrownBy(() -> service.requireCredentials("", "secret"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);

        assertThatThrownBy(() -> service.requireCredentials("alice", ""))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void requireCredentialsShouldAcceptPresentFields() {
        assertThatCode(() -> service.requireCredentials("alice", "secret")).doesNotThrowAnyException();
    }
}
