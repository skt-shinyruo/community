package com.nowcoder.community.gateway.edge;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "gateway:rate-limit:";

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean allow(String key, RateLimitProperties.Policy policy) {
        if (policy == null) {
            return true;
        }
        int limit = Math.max(1, policy.getLimit());
        Duration window = policy.getWindow() == null ? Duration.ofMinutes(1) : policy.getWindow();
        String redisKey = KEY_PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            throw new IllegalStateException("redis increment returned null");
        }
        if (count == 1L) {
            redisTemplate.expire(redisKey, window);
        }
        return count <= limit;
    }
}
