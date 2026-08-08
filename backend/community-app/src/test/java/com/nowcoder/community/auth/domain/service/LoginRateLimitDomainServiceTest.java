package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.domain.model.LoginRateLimitKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitDomainServiceTest {

    private final LoginRateLimitDomainService service = new LoginRateLimitDomainService();

    @Test
    void keyOfShouldTrimSubjectAndIpWithoutGuessingDatabaseCollation() {
        LoginRateLimitKey key = service.keyOf(" Alice ", " 127.0.0.1 ");

        assertThat(key.subject()).isEqualTo("Alice");
        assertThat(key.ip()).isEqualTo("127.0.0.1");
    }

    @Test
    void keyOfShouldKeepDistinctInputsDistinctUntilTheOwnerReturnsAnAuthoritativeSubject() {
        assertThat(service.keyOf("alice", null).subject())
                .isNotEqualTo(service.keyOf("ałice", null).subject());
        assertThat(service.keyOf("ae", null).subject())
                .isNotEqualTo(service.keyOf("æ", null).subject());
        assertThat(service.keyOf("は", null).subject())
                .isNotEqualTo(service.keyOf("ハ", null).subject());
    }
}
