package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.ContentHotPathProperties;
import com.nowcoder.community.content.application.HotPathSingleFlight;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
@ConditionalOnExpression("'${content.storage:redis}' == 'redis' && '${content.hot-path.single-flight.enabled:true}' == 'true'")
public class RedisHotPathSingleFlight implements HotPathSingleFlight {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ContentHotPathProperties properties;

    public RedisHotPathSingleFlight(StringRedisTemplate redisTemplate, ContentHotPathProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties == null ? new ContentHotPathProperties() : properties;
    }

    @Override
    public <T> T execute(String scope, String key, Duration ttl, Supplier<T> loader, Supplier<T> fallbackWhenBusy) {
        if (!StringUtils.hasText(scope) || !StringUtils.hasText(key) || loader == null) {
            return loader == null ? null : loader.get();
        }
        String lockKey = "sf:hot-path:" + scope.trim() + ":" + key.trim();
        String token = UUID.randomUUID().toString();
        Duration lockTtl = positive(ttl == null ? properties.getSingleFlight().ttl() : ttl);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, lockTtl);
        if (!Boolean.TRUE.equals(acquired)) {
            return fallbackWhenBusy == null ? null : fallbackWhenBusy.get();
        }
        try {
            return loader.get();
        } finally {
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
        }
    }

    private static Duration positive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            return Duration.ofMillis(1L);
        }
        return value;
    }
}
