package com.nowcoder.community.common.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Redis 幂等存储实现：
 * - 使用 SETNX + TTL 实现 PROCESSING 锁
 * - 使用 SET 覆盖写入 SUCCEEDED 响应
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final RedisScript<Long> EXTEND_PROCESSING_SCRIPT = script(
            """
                    if redis.call('get', KEYS[1]) == 'P' then
                        return redis.call('pexpire', KEYS[1], ARGV[1])
                    end
                    return 0
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquireProcessing(String operation, int userId, String key, Duration ttl) {
        if (!StringUtils.hasText(operation)) {
            throw new IllegalArgumentException("operation is blank");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId is invalid");
        }
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("key is blank");
        }
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        Duration safeTtl = ttl == null ? Duration.ofSeconds(30) : ttl;
        String storeKey = buildStoreKey(operation, userId, key);
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(storeKey, "P", safeTtl));
    }

    @Override
    public Entry get(String operation, int userId, String key) {
        if (!StringUtils.hasText(operation) || userId <= 0 || !StringUtils.hasText(key)) {
            return null;
        }
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        String storeKey = buildStoreKey(operation, userId, key);
        String value = redisTemplate.opsForValue().get(storeKey);
        if (value == null) {
            return null;
        }
        if ("P".equals(value)) {
            return new Entry(Status.PROCESSING, null);
        }
        if (value.startsWith("S\n")) {
            return new Entry(Status.SUCCESS, value.substring(2));
        }
        throw new IllegalStateException("unknown idempotency state");
    }

    @Override
    public void saveSuccess(String operation, int userId, String key, String successJson, Duration ttl) {
        if (!StringUtils.hasText(operation)) {
            throw new IllegalArgumentException("operation is blank");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId is invalid");
        }
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("key is blank");
        }
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        Duration safeTtl = ttl == null ? Duration.ofHours(24) : ttl;
        String storeKey = buildStoreKey(operation, userId, key);
        String json = successJson == null ? "null" : successJson;
        redisTemplate.opsForValue().set(storeKey, "S\n" + json, safeTtl);
    }

    @Override
    public void extendProcessing(String operation, int userId, String key, Duration ttl) {
        if (!StringUtils.hasText(operation)) {
            throw new IllegalArgumentException("operation is blank");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId is invalid");
        }
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("key is blank");
        }
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        Duration safeTtl = ttl == null ? Duration.ofSeconds(30) : ttl;
        redisTemplate.execute(
                EXTEND_PROCESSING_SCRIPT,
                List.of(buildStoreKey(operation, userId, key)),
                Long.toString(Math.max(1L, safeTtl.toMillis()))
        );
    }

    @Override
    public void delete(String operation, int userId, String key) {
        if (!StringUtils.hasText(operation) || userId <= 0 || !StringUtils.hasText(key)) {
            return;
        }
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        redisTemplate.delete(buildStoreKey(operation, userId, key));
    }

    private String buildStoreKey(String operation, int userId, String key) {
        String op = operation.trim().toLowerCase(Locale.ROOT);
        return "idem:" + op + ":" + userId + ":" + key.trim();
    }

    private static <T> RedisScript<T> script(String scriptText, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(resultType);
        return script;
    }
}
