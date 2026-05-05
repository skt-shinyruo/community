package com.nowcoder.community.gateway.edge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRateLimiterRemovalTest {

    @Test
    void gatewayShouldNotShipInMemoryRateLimiterFallback() {
        assertThatThrownBy(() -> Class.forName("com.nowcoder.community.gateway.edge.InMemoryRateLimiter"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
