package com.nowcoder.community.gateway.edge;

import com.nowcoder.community.gateway.CommunityGatewayApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                CommunityGatewayApplication.class,
                RateLimitSecurityOrderIntegrationTest.AuthenticatedRateLimitConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class RateLimitSecurityOrderIntegrationTest {

    private static final String PROBE_PATH = "/api/rate-limit-order-probe";
    private static final BlockingQueue<String> RATE_LIMIT_KEYS = new LinkedBlockingQueue<>();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.discovery.locator.enabled", () -> "false");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
    }

    @BeforeEach
    void clearCapturedKeys() {
        RATE_LIMIT_KEYS.clear();
    }

    @Test
    void shouldUseAuthenticatedPrincipalAfterSecurityFilterChainRuns() throws Exception {
        webTestClient.get()
                .uri(PROBE_PATH)
                .headers(headers -> headers.setBasicAuth("alice", "password"))
                .exchange()
                .expectStatus().isNotFound();

        assertThat(RATE_LIMIT_KEYS.poll(5, TimeUnit.SECONDS))
                .isEqualTo("principal:alice:" + PROBE_PATH);
        assertThat(RATE_LIMIT_KEYS).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthenticatedRateLimitConfig {

        @Bean
        @Primary
        RateLimitProperties testRateLimitProperties() {
            RateLimitProperties properties = new RateLimitProperties();
            properties.getPolicies().put(PROBE_PATH, new RateLimitProperties.Policy());
            return properties;
        }

        @Bean
        @Primary
        RateLimiter recordingRateLimiter() {
            return (key, policy) -> {
                RATE_LIMIT_KEYS.add(key);
                return true;
            };
        }

        @Bean
        MapReactiveUserDetailsService testUsers() {
            return new MapReactiveUserDetailsService(User.withUsername("alice")
                    .password("{noop}password")
                    .roles("USER")
                    .build());
        }

        @Bean
        @Order(0)
        SecurityWebFilterChain authenticatedProbeSecurityWebFilterChain(ServerHttpSecurity http) {
            return http
                    .securityMatcher(ServerWebExchangeMatchers.pathMatchers(PROBE_PATH))
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .httpBasic(Customizer.withDefaults())
                    .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                    .logout(ServerHttpSecurity.LogoutSpec::disable)
                    .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                    .build();
        }
    }
}
