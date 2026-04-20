package com.nowcoder.community.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "auth.refresh.store", havingValue = "redis")
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final DefaultRedisScript<Long> STORE_SCRIPT = new DefaultRedisScript<>();

    private static final String KEY_PREFIX_TOKEN = "auth:refresh:";
    private static final String KEY_PREFIX_FAMILY = "auth:refresh:family:";
    private static final String KEY_PREFIX_FAMILY_REVOKED = "auth:refresh:family:revoked:";

    static {
        STORE_SCRIPT.setResultType(Long.class);
        STORE_SCRIPT.setScriptText(
                "if redis.call('exists', KEYS[1]) == 1 then return 0 end " +
                        "redis.call('set', KEYS[2], ARGV[1], 'EX', ARGV[2]) " +
                        "redis.call('sadd', KEYS[3], ARGV[3]) " +
                        "redis.call('expire', KEYS[3], ARGV[2]) " +
                        "return 1"
        );
    }

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void store(String refreshToken, int userId, String familyId, Instant expiresAt) {
        if (!StringUtils.hasText(refreshToken) || userId <= 0 || !StringUtils.hasText(familyId) || expiresAt == null) {
            return;
        }
        String token = refreshToken.trim();
        String family = familyId.trim();
        if (token.isEmpty() || family.isEmpty()) {
            return;
        }

        StoredRefreshToken record = new StoredRefreshToken(token, userId, family, expiresAt);
        try {
            String json = objectMapper.writeValueAsString(record);
            long ttlSeconds = Math.max(1, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
            Long stored = redisTemplate.execute(
                    STORE_SCRIPT,
                    List.of(
                            KEY_PREFIX_FAMILY_REVOKED + family,
                            KEY_PREFIX_TOKEN + token,
                            KEY_PREFIX_FAMILY + family
                    ),
                    json,
                    Long.toString(ttlSeconds),
                    token
            );
            if (stored == null || stored <= 0) {
                throw new IllegalStateException("refresh token family 已被撤销");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("refresh token 序列化失败", e);
        }
    }

    @Override
    public StoredRefreshToken find(String refreshToken) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN + refreshToken);
        return readRecord(json);
    }

    @Override
    public StoredRefreshToken consume(String refreshToken) {
        String json = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX_TOKEN + refreshToken);
        StoredRefreshToken found = readRecord(json);
        if (found != null) {
            redisTemplate.opsForSet().remove(KEY_PREFIX_FAMILY + found.familyId(), refreshToken);
        }
        return found;
    }

    private StoredRefreshToken readRecord(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StoredRefreshToken.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public void revoke(String refreshToken) {
        StoredRefreshToken found = find(refreshToken);
        redisTemplate.delete(KEY_PREFIX_TOKEN + refreshToken);
        if (found != null) {
            redisTemplate.opsForSet().remove(KEY_PREFIX_FAMILY + found.familyId(), refreshToken);
        }
    }

    @Override
    public void revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return;
        }

        String family = familyId.trim();
        if (family.isEmpty()) {
            return;
        }

        String familyKey = KEY_PREFIX_FAMILY + family;
        String revokedKey = KEY_PREFIX_FAMILY_REVOKED + family;

        long markerTtlSeconds = TimeUnit.DAYS.toSeconds(7);
        try {
            Long ttlSeconds = redisTemplate.getExpire(familyKey, TimeUnit.SECONDS);
            if (ttlSeconds != null && ttlSeconds > 0) {
                markerTtlSeconds = ttlSeconds;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            redisTemplate.opsForValue().set(revokedKey, "1", markerTtlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException ignored) {
        }

        Set<String> members = redisTemplate.opsForSet().members(familyKey);
        if (members != null) {
            for (String token : members) {
                redisTemplate.delete(KEY_PREFIX_TOKEN + token);
            }
        }
        redisTemplate.delete(familyKey);
    }
}
