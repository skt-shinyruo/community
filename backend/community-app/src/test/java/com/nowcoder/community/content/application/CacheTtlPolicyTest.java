package com.nowcoder.community.content.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CacheTtlPolicyTest {

    @Test
    void jitteredTtlShouldStayWithinConfiguredRange() {
        ContentHotPathProperties properties = new ContentHotPathProperties();
        properties.getCache().setTtlJitterSeconds(60);
        CacheTtlPolicy policy = new CacheTtlPolicy(properties);

        Duration ttl = policy.jitteredTtl("post:summary:1", Duration.ofSeconds(300));

        assertThat(ttl).isGreaterThanOrEqualTo(Duration.ofSeconds(300));
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofSeconds(360));
    }

    @Test
    void jitteredTtlShouldBeStableForSameKey() {
        ContentHotPathProperties properties = new ContentHotPathProperties();
        properties.getCache().setTtlJitterSeconds(60);
        CacheTtlPolicy policy = new CacheTtlPolicy(properties);

        Duration first = policy.jitteredTtl("post:detail:1", Duration.ofSeconds(120));
        Duration second = policy.jitteredTtl("post:detail:1", Duration.ofSeconds(120));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void jitteredTtlShouldReturnPositiveTtlWhenBaseIsInvalid() {
        ContentHotPathProperties properties = new ContentHotPathProperties();
        properties.getCache().setTtlJitterSeconds(60);
        CacheTtlPolicy policy = new CacheTtlPolicy(properties);

        Duration ttl = policy.jitteredTtl("post:detail:1", Duration.ZERO);

        assertThat(ttl).isEqualTo(Duration.ofSeconds(1));
    }
}
