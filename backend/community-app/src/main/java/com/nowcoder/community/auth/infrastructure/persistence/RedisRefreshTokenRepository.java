package com.nowcoder.community.auth.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "auth.refresh.store", havingValue = "redis")
public class RedisRefreshTokenRepository implements RefreshTokenRepository {

    private static final DefaultRedisScript<Long> STORE_SCRIPT = new DefaultRedisScript<>();

    private static final String KEY_PREFIX_TOKEN = "auth:refresh:";
    private static final String KEY_PREFIX_TOKEN_REVOKED = "auth:refresh:revoked:";
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

    public RedisRefreshTokenRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void store(String refreshToken, UUID userId, String familyId, Instant expiresAt) {
        if (!StringUtils.hasText(refreshToken) || userId == null || !StringUtils.hasText(familyId) || expiresAt == null) {
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
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        String token = refreshToken.trim();
        if (token.isEmpty()) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN + token);
        return readRecord(json);
    }

    @Override
    public StoredRefreshToken consume(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        String token = refreshToken.trim();
        if (token.isEmpty()) {
            return null;
        }
        String json = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX_TOKEN + token);
        StoredRefreshToken found = readRecord(json);
        if (found != null) {
            writeTombstone(found, Instant.now());
            String member = StringUtils.hasText(found.refreshToken()) ? found.refreshToken().trim() : token;
            if (!member.isEmpty()) {
                try {
                    redisTemplate.opsForSet().remove(KEY_PREFIX_FAMILY + found.familyId(), member);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return found;
    }

    @Override
    public RevokedRefreshToken findRevoked(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        String token = refreshToken.trim();
        if (token.isEmpty()) {
            return null;
        }
        return readTombstone(token, redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN_REVOKED + token));
    }

    private void writeTombstone(StoredRefreshToken record, Instant revokedAt) {
        if (record == null || !StringUtils.hasText(record.refreshToken()) || !StringUtils.hasText(record.familyId()) || record.expiresAt() == null) {
            return;
        }
        Instant now = Instant.now();
        if (!record.expiresAt().isAfter(now)) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(new Tombstone(record.userId(), record.familyId(), record.expiresAt(), revokedAt));
            long ttlSeconds = Math.max(1, record.expiresAt().getEpochSecond() - now.getEpochSecond());
            redisTemplate.opsForValue().set(KEY_PREFIX_TOKEN_REVOKED + record.refreshToken().trim(), json, ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException ignored) {
        }
    }

    private RevokedRefreshToken readTombstone(String refreshToken, String json) {
        if (json == null) {
            return null;
        }
        try {
            Tombstone tombstone = objectMapper.readValue(json, Tombstone.class);
            return new RevokedRefreshToken(refreshToken, tombstone.userId(), tombstone.familyId(), tombstone.expiresAt(), tombstone.revokedAt());
        } catch (JsonProcessingException e) {
            return null;
        }
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
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        String token = refreshToken.trim();
        if (token.isEmpty()) {
            return;
        }
        StoredRefreshToken found = find(token);
        redisTemplate.delete(KEY_PREFIX_TOKEN + token);
        if (found != null) {
            writeTombstone(found, Instant.now());
            String member = StringUtils.hasText(found.refreshToken()) ? found.refreshToken().trim() : token;
            if (!member.isEmpty()) {
                try {
                    redisTemplate.opsForSet().remove(KEY_PREFIX_FAMILY + found.familyId(), member);
                } catch (RuntimeException ignored) {
                }
            }
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
                if (!StringUtils.hasText(token)) {
                    continue;
                }
                String member = token.trim();
                writeTombstone(readRecord(redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN + member)), Instant.now());
                redisTemplate.delete(KEY_PREFIX_TOKEN + member);
            }
        }
        redisTemplate.delete(familyKey);
    }

    private record Tombstone(UUID userId, String familyId, Instant expiresAt, Instant revokedAt) {
    }
}
