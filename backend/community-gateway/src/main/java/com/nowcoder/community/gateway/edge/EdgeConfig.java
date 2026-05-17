package com.nowcoder.community.gateway.edge;

import com.nowcoder.community.gateway.canary.CanaryRouteProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        RateLimitProperties.class,
        TrafficPolicyProperties.class,
        CanaryRouteProperties.class
})
public class EdgeConfig {

    @Bean
    RateLimiter edgeRedisRateLimiter(StringRedisTemplate redisTemplate) {
        return new RedisRateLimiter(redisTemplate);
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
