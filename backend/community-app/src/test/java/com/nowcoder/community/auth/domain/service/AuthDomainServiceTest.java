package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(service.requireCredentials("  alice  ", "secret")).isEqualTo("alice");
    }

    @Test
    void requireCredentialsShouldRejectControlFormatAndDefaultIgnorableCharacters() {
        assertInvalidUsername("a\u0000lice");
        assertInvalidUsername("a\u200Blice");
        assertInvalidUsername("a\u200Dlice");
        assertInvalidUsername("alice\u202E");
        assertInvalidUsername("ali\uFE0Fce");
        assertInvalidUsername("ali\uD800ce");
    }

    private void assertInvalidUsername(String username) {
        assertThatThrownBy(() -> service.requireCredentials(username, "secret"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    }
}
