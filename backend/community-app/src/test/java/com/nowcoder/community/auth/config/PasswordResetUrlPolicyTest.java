package com.nowcoder.community.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetUrlPolicyTest {

    @Test
    void normalizeShouldKeepAValidHttpsPathAndRemoveTrailingSlash() {
        assertThat(PasswordResetUrlPolicy.normalizeHttpsBaseUrl(
                " https://community.example/account/ "
        )).isEqualTo("https://community.example/account");
    }

    @Test
    void normalizeShouldAcceptValidExplicitPortBoundaries() {
        for (int port : new int[]{1, 8443, 65_535}) {
            assertThat(PasswordResetUrlPolicy.normalizeHttpsBaseUrl(
                    "https://community.example:" + port + "/reset"
            )).isEqualTo("https://community.example:" + port + "/reset");
        }
    }

    @Test
    void normalizeShouldRejectEmptyOutOfRangeOrZeroPort() {
        for (String value : new String[]{
                "https://community.example:/reset",
                "https://community.example:0",
                "https://community.example:65536"
        }) {
            assertThatThrownBy(() -> PasswordResetUrlPolicy.normalizeHttpsBaseUrl(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
