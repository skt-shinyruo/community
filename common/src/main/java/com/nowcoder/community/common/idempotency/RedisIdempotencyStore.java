package com.nowcoder.community.common.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Redis 幂等存储实现：
 * - 使用 SETNX + TTL 实现 PROCESSING 锁
 * - 使用 SET 覆盖写入 SUCCEEDED 响应
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquireProcessing(String key, Duration ttl) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("key is blank");
        }
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        Duration safeTtl = ttl == null ? Duration.ofSeconds(30) : ttl;
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "P", safeTtl));
    }

    @Override
    public String get(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void save(String key, String value, Duration ttl) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("key is blank");
        }
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        Duration safeTtl = ttl == null ? Duration.ofHours(24) : ttl;
        redisTemplate.opsForValue().set(key, value == null ? "" : value, safeTtl);
    }

    @Override
    public void delete(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        redisTemplate.delete(key);
    }
}

