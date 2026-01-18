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

    public RedisCaptchaStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String owner, String code, Duration ttl) {
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(code) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(key(owner), code, ttl);
    }

    @Override
    public String get(String owner) {
        if (!StringUtils.hasText(owner)) {
            return null;
        }
        return redisTemplate.opsForValue().get(key(owner));
    }

    @Override
    public void delete(String owner) {
        if (!StringUtils.hasText(owner)) {
            return;
        }
        redisTemplate.delete(key(owner));
        redisTemplate.delete(failKey(owner));
    }

    @Override
    public int incrementFailures(String owner, Duration ttl) {
        if (!StringUtils.hasText(owner) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return 0;
        }
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
    }

    private String key(String owner) {
        return PREFIX + owner;
    }

    private String failKey(String owner) {
        return PREFIX_FAIL + owner;
    }
}
