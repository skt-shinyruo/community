package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.constants.ValidationLimits;
import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyDomainServiceTest {

    private final PasswordPolicyDomainService service = new PasswordPolicyDomainService();

    @Test
    void requireValidPasswordShouldRejectBlankPassword() {
        assertInvalid("  ");
    }

    @Test
    void requireValidPasswordShouldRejectTooShortPassword() {
        assertInvalid("Abc123!");
    }

    @Test
    void requireValidPasswordShouldRejectTooLongPassword() {
        assertInvalid("Aa" + "1".repeat(ValidationLimits.PASSWORD_MAX - 1));
    }

    @Test
    void requireValidPasswordShouldRejectSingleClassWeakPassword() {
        assertInvalid("aaaaaaaa");
    }

    @Test
    void requireValidPasswordShouldReturnValidPasswordWithoutTrimming() {
        assertThat(service.requireValidPassword("abcdefg1")).isEqualTo("abcdefg1");
    }

    @Test
    void requireValidPasswordShouldRejectLeadingOrTrailingWhitespace() {
        assertInvalid(" abcdefg1 ");
    }

    private void assertInvalid(String password) {
        assertThatThrownBy(() -> service.requireValidPassword(password))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(INVALID_ARGUMENT);
    }
}
