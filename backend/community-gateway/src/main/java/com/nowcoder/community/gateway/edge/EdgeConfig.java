package com.nowcoder.community.gateway.edge;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RateLimitProperties.class, TrafficPolicyProperties.class})
public class EdgeConfig {

    @Bean
    InMemoryRateLimiter inMemoryRateLimiter() {
        return new InMemoryRateLimiter();
    }

    @Bean
    RateLimitWebFilter rateLimitWebFilter(RateLimitProperties properties, InMemoryRateLimiter limiter) {
        return new RateLimitWebFilter(properties, limiter);
    }

    @Bean
    TrafficPolicyEvaluator trafficPolicyEvaluator(TrafficPolicyProperties properties) {
        return new TrafficPolicyEvaluator(properties);
    }

    @Bean
    AccessLogWebFilter accessLogWebFilter() {
        return new AccessLogWebFilter();
    }
}
