package com.nowcoder.community.common.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Redis 幂等存储实现：
 * - 使用 SETNX + TTL 实现 PROCESSING 锁
 * - 使用 SET 覆盖写入 SUCCEEDED 响应
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final RedisScript<Long> EXTEND_PROCESSING_SCRIPT = script(
            """
                    local value = redis.call('get', KEYS[1])
                    local hash = value and string.sub(value, 3) or nil
                    if value
                            and string.sub(value, 1, 2) == 'P\\n'
                            and hash ~= ''
                            and not string.find(hash, string.char(10), 1, true)
                            and not string.find(hash, string.char(13), 1, true) then
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
    public boolean tryAcquireProcessing(String operation, UUID userId, String key, String requestHash, Duration ttl) {
        if (!StringUtils.hasText(operation)) {
            throw new IllegalArgumentException("operation is blank");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is invalid");
        }
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("key is blank");
        }
        String hash = normalizeRequestHash(requestHash);
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        Duration safeTtl = ttl == null ? Duration.ofSeconds(30) : ttl;
        String storeKey = buildStoreKey(operation, userId, key);
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(storeKey, processingValue(hash), safeTtl));
    }

    @Override
    public Entry get(String operation, UUID userId, String key) {
        if (!StringUtils.hasText(operation) || userId == null || !StringUtils.hasText(key)) {
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
        if (value.startsWith("P\n")) {
            String requestHash = value.substring(2);
            if (!StringUtils.hasText(requestHash)) {
                throw invalidState();
            }
            return new Entry(Status.PROCESSING, null, requestHash);
        }
        if (value.startsWith("S\n")) {
            String payload = value.substring(2);
            int separator = payload.indexOf('\n');
            if (separator <= 0) {
                throw invalidState();
            }
            String requestHash = payload.substring(0, separator);
            if (!StringUtils.hasText(requestHash)) {
                throw invalidState();
            }
            return new Entry(Status.SUCCESS, payload.substring(separator + 1), requestHash);
        }
        throw invalidState();
    }

    @Override
    public void saveSuccess(String operation, UUID userId, String key, String requestHash, String successJson, Duration ttl) {
        if (!StringUtils.hasText(operation)) {
            throw new IllegalArgumentException("operation is blank");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is invalid");
        }
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("key is blank");
        }
        String hash = normalizeRequestHash(requestHash);
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        Duration safeTtl = ttl == null ? Duration.ofHours(24) : ttl;
        String storeKey = buildStoreKey(operation, userId, key);
        String json = successJson == null ? "null" : successJson;
        redisTemplate.opsForValue().set(storeKey, successValue(hash, json), safeTtl);
    }

    @Override
    public void extendProcessing(String operation, UUID userId, String key, Duration ttl) {
        if (!StringUtils.hasText(operation)) {
            throw new IllegalArgumentException("operation is blank");
        }
        if (userId == null) {
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
    public void delete(String operation, UUID userId, String key) {
        if (!StringUtils.hasText(operation) || userId == null || !StringUtils.hasText(key)) {
            return;
        }
        if (redisTemplate == null) {
            throw new IllegalStateException("redisTemplate is null");
        }
        redisTemplate.delete(buildStoreKey(operation, userId, key));
    }

    private String buildStoreKey(String operation, UUID userId, String key) {
        String op = operation.trim().toLowerCase(Locale.ROOT);
        return "idem:" + op + ":" + userId + ":" + key.trim();
    }

    private String processingValue(String requestHash) {
        return "P\n" + requestHash;
    }

    private String successValue(String requestHash, String json) {
        return "S\n" + requestHash + "\n" + json;
    }

    private String normalizeRequestHash(String requestHash) {
        return IdempotencyStore.requireRequestHash(requestHash);
    }

    private IllegalStateException invalidState() {
        return new IllegalStateException("unknown idempotency state");
    }

    private static <T> RedisScript<T> script(String scriptText, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(resultType);
        return script;
    }
}
