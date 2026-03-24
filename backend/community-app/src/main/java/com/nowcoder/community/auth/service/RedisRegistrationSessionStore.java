package com.nowcoder.community.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "auth.registration.session.store", havingValue = "redis", matchIfMissing = true)
public class RedisRegistrationSessionStore implements RegistrationSessionStore {

    private static final String KEY_PREFIX = "auth:regsession:";

    private final StringRedisTemplate redisTemplate;

    public RedisRegistrationSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String issue(int userId, Duration ttl) {
        if (userId <= 0 || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return null;
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(key(token), Integer.toString(userId), ttl);
        return token;
    }

    @Override
    public Integer findUserId(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return null;
        }

        String token = registrationToken.trim();
        String raw = redisTemplate.opsForValue().get(key(token));
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        try {
            int userId = Integer.parseInt(raw.trim());
            if (userId <= 0) {
                redisTemplate.delete(key(token));
                return null;
            }
            return userId;
        } catch (NumberFormatException ex) {
            redisTemplate.delete(key(token));
            return null;
        }
    }

    @Override
    public void delete(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return;
        }

        try {
            redisTemplate.delete(key(registrationToken.trim()));
        } catch (RuntimeException ignored) {
            // best-effort cleanup
        }
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}

