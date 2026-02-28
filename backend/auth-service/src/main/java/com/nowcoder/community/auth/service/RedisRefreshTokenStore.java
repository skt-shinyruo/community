package com.nowcoder.community.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "auth.refresh.store", havingValue = "redis")
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX_TOKEN = "auth:refresh:";
    private static final String KEY_PREFIX_FAMILY = "auth:refresh:family:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void store(String refreshToken, int userId, String familyId, Instant expiresAt) {
        StoredRefreshToken record = new StoredRefreshToken(refreshToken, userId, familyId, expiresAt);
        try {
            String json = objectMapper.writeValueAsString(record);
            long ttlSeconds = Math.max(1, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
            redisTemplate.opsForValue().set(KEY_PREFIX_TOKEN + refreshToken, json, ttlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForSet().add(KEY_PREFIX_FAMILY + familyId, refreshToken);
            redisTemplate.expire(KEY_PREFIX_FAMILY + familyId, ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("refresh token 序列化失败", e);
        }
    }

    @Override
    public StoredRefreshToken find(String refreshToken) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN + refreshToken);
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
        String familyKey = KEY_PREFIX_FAMILY + familyId;
        Set<String> members = redisTemplate.opsForSet().members(familyKey);
        if (members != null) {
            for (String token : members) {
                redisTemplate.delete(KEY_PREFIX_TOKEN + token);
            }
        }
        redisTemplate.delete(familyKey);
    }
}
