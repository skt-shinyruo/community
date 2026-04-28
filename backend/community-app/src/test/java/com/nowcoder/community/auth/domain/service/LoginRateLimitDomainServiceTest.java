package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.domain.model.LoginRateLimitKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitDomainServiceTest {

    private final LoginRateLimitDomainService service = new LoginRateLimitDomainService();

    @Test
    void keyOfShouldNormalizeUsernameAndIp() {
        LoginRateLimitKey key = service.keyOf(" Alice ", " 127.0.0.1 ");

        assertThat(key.username()).isEqualTo("alice");
        assertThat(key.ip()).isEqualTo("127.0.0.1");
    }
}
