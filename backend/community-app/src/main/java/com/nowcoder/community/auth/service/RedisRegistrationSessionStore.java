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
    public String issue(UUID userId, Duration ttl) {
        if (userId == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return null;
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(key(token), userId.toString(), ttl);
        return token;
    }

    @Override
    public UUID findUserId(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return null;
        }

        String token = registrationToken.trim();
        String raw = redisTemplate.opsForValue().get(key(token));
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
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
