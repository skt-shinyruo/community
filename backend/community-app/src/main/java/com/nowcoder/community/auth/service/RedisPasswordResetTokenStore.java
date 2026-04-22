package com.nowcoder.community.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "auth.password-reset.store", havingValue = "redis", matchIfMissing = true)
public class RedisPasswordResetTokenStore implements PasswordResetTokenStore {

    private static final String KEY_PREFIX = "auth:pwdreset:";

    private final StringRedisTemplate redisTemplate;

    public RedisPasswordResetTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void store(String token, UUID userId, Duration ttl) {
        if (!StringUtils.hasText(token) || userId == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, userId.toString(), ttl);
    }

    @Override
    public UUID consume(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + token.trim());
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
