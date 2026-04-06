package com.nowcoder.community.gateway.http;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.discovery.GatewayDiscoveryClientAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayDiscoveryClasspathTest {

    @Test
    void shouldExposeGatewayAndDiscoveryInfrastructure() {
        assertThat(RouteLocator.class).isNotNull();
        assertThat(ReactiveDiscoveryClient.class).isNotNull();
        assertThat(GatewayAutoConfiguration.class).isNotNull();
        assertThat(GatewayDiscoveryClientAutoConfiguration.class).isNotNull();
    }
}
