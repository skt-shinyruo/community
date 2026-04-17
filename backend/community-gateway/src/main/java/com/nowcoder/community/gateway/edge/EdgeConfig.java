package com.nowcoder.community.gateway.edge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RateLimitProperties.class, TrafficPolicyProperties.class})
public class EdgeConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    RateLimiter redisRateLimiter(StringRedisTemplate redisTemplate) {
        return new RedisRateLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    RateLimiter inMemoryRateLimiter() {
        return new InMemoryRateLimiter();
    }

    @Bean
    RateLimitWebFilter rateLimitWebFilter(RateLimitProperties properties, RateLimiter limiter) {
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
