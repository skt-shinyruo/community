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

    @Test
    void requireValidPasswordShouldAcceptExactlySeventyTwoUtf8Bytes() {
        String password = "A1" + "\u5bc6".repeat(23) + "a";

        assertThat(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
        assertThat(service.requireValidPassword(password)).isEqualTo(password);
    }

    @Test
    void requireValidPasswordShouldRejectSeventyThreeUtf8Bytes() {
        String password = "A1" + "\u5bc6".repeat(23) + "ab";

        assertThat(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(73);
        assertInvalid(password);
    }

    @Test
    void requireValidPasswordShouldHandleSupplementaryCodePointsAtBcryptBoundary() {
        String accepted = "A1" + "\uD83D\uDE00".repeat(17) + "aa";
        String rejected = accepted + "a";

        assertThat(accepted.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
        assertThat(rejected.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(73);
        assertThat(service.requireValidPassword(accepted)).isEqualTo(accepted);
        assertInvalid(rejected);
    }

    @Test
    void requireValidPasswordShouldRejectUnicodeBoundarySpacesWithoutChangingInternalSpaces() {
        assertInvalid("\u00A0abcdefg1");
        assertInvalid("abcdefg1\u3000");
        assertThat(service.requireValidPassword("abc\u00A0def1")).isEqualTo("abc\u00A0def1");
    }

    private void assertInvalid(String password) {
        assertThatThrownBy(() -> service.requireValidPassword(password))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(INVALID_ARGUMENT);
    }
}
