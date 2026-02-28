package com.nowcoder.community.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "auth.password-reset.store", havingValue = "redis", matchIfMissing = true)
public class RedisPasswordResetTokenStore implements PasswordResetTokenStore {

    private static final String KEY_PREFIX = "auth:pwdreset:";

    private final StringRedisTemplate redisTemplate;

    public RedisPasswordResetTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void store(String token, int userId, Duration ttl) {
        if (!StringUtils.hasText(token) || userId <= 0 || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, Integer.toString(userId), ttl);
    }

    @Override
    public Integer consume(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String key = KEY_PREFIX + token.trim();
        String value = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            int userId = Integer.parseInt(value);
            return userId > 0 ? userId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
