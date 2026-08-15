package com.nowcoder.community.auth.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitKeyTest {

    @Test
    void keyOfShouldTrimSubjectAndIpWithoutGuessingDatabaseCollation() {
        LoginRateLimitKey key = new LoginRateLimitKey(" Alice ", " 127.0.0.1 ");

        assertThat(key.subject()).isEqualTo("Alice");
        assertThat(key.ip()).isEqualTo("127.0.0.1");
    }

    @Test
    void keyOfShouldKeepDistinctInputsDistinctUntilTheOwnerReturnsAnAuthoritativeSubject() {
        assertThat(new LoginRateLimitKey("alice", null).subject())
                .isNotEqualTo(new LoginRateLimitKey("ałice", null).subject());
        assertThat(new LoginRateLimitKey("ae", null).subject())
                .isNotEqualTo(new LoginRateLimitKey("æ", null).subject());
        assertThat(new LoginRateLimitKey("は", null).subject())
                .isNotEqualTo(new LoginRateLimitKey("ハ", null).subject());
    }
}
