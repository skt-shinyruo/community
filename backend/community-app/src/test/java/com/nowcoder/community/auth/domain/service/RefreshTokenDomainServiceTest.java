package com.nowcoder.community.auth.domain.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenDomainServiceTest {

    private final RefreshTokenDomainService service = new RefreshTokenDomainService();

    @Test
    void isExpiredShouldTreatNullOrPastAsExpired() {
        Instant now = Instant.parse("2026-04-28T00:00:00Z");

        assertThat(service.isExpired(null, now)).isTrue();
        assertThat(service.isExpired(now.minusSeconds(1), now)).isTrue();
        assertThat(service.isExpired(now.plusSeconds(1), now)).isFalse();
    }

    @Test
    void shouldRevokeFamilyOnReuseOnlyWhenRevokedTokenIsUnexpiredAndOutsideGrace() {
        Instant now = Instant.parse("2026-04-28T00:00:00Z");

        assertThat(service.shouldRevokeFamilyOnReuse(now.minusSeconds(11), now.plusSeconds(60), now, 10)).isTrue();
        assertThat(service.shouldRevokeFamilyOnReuse(now.minusSeconds(10), now.plusSeconds(60), now, 10)).isFalse();
        assertThat(service.shouldRevokeFamilyOnReuse(now.minusSeconds(11), now.minusSeconds(1), now, 10)).isFalse();
        assertThat(service.shouldRevokeFamilyOnReuse(null, now.plusSeconds(60), now, 10)).isFalse();
    }
}
