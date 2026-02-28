package com.nowcoder.community.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "auth.captcha.store", havingValue = "redis", matchIfMissing = true)
public class RedisCaptchaStore implements CaptchaStore {

    private static final String PREFIX = "captcha:";
    private static final String PREFIX_FAIL = "captcha:fail:";

    private final StringRedisTemplate redisTemplate;
    private final InMemoryCaptchaStore fallback = new InMemoryCaptchaStore();

    public RedisCaptchaStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String owner, String code, Duration ttl) {
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(code) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(owner), code, ttl);
        } catch (RuntimeException e) {
            fallback.save(owner, code, ttl);
        }
    }

    @Override
    public String get(String owner) {
        if (!StringUtils.hasText(owner)) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key(owner));
        } catch (RuntimeException e) {
            return fallback.get(owner);
        }
    }

    @Override
    public void delete(String owner) {
        if (!StringUtils.hasText(owner)) {
            return;
        }
        try {
            redisTemplate.delete(key(owner));
            redisTemplate.delete(failKey(owner));
        } catch (RuntimeException e) {
            fallback.delete(owner);
        }
    }

    @Override
    public int incrementFailures(String owner, Duration ttl) {
        if (!StringUtils.hasText(owner) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return 0;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(failKey(owner));
            if (count == null) {
                return 0;
            }
            if (count == 1) {
                redisTemplate.expire(failKey(owner), ttl);
            }
            if (count > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return count.intValue();
        } catch (RuntimeException e) {
            return fallback.incrementFailures(owner, ttl);
        }
    }

    private String key(String owner) {
        return PREFIX + owner;
    }

    private String failKey(String owner) {
        return PREFIX_FAIL + owner;
    }
}
