package com.nowcoder.community.gateway.edge;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        RateLimitProperties.class,
        EdgeTrustedProxyProperties.class
})
public class EdgeConfig {

    @Bean
    RedisRateLimiter edgeRedisRateLimiter(StringRedisTemplate redisTemplate) {
        return new RedisRateLimiter(redisTemplate);
    }

    @Bean
    RateLimitWebFilter rateLimitWebFilter(RateLimitProperties properties, RedisRateLimiter limiter) {
        return new RateLimitWebFilter(properties, limiter);
    }

    @Bean
    ForwardedHeaderCanonicalizationWebFilter forwardedHeaderCanonicalizationWebFilter(
            EdgeTrustedProxyProperties properties,
            ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        return new ForwardedHeaderCanonicalizationWebFilter(properties, meterRegistryProvider.getIfAvailable());
    }

    @Bean
    CanonicalForwardedForHttpHeadersFilter canonicalForwardedForHttpHeadersFilter() {
        return new CanonicalForwardedForHttpHeadersFilter();
    }

    @Bean
    AccessLogWebFilter accessLogWebFilter() {
        return new AccessLogWebFilter();
    }
}
