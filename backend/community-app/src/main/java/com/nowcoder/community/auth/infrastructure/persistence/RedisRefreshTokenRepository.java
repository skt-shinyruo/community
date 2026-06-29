package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
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
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<String> BEGIN_ROTATION_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> FINISH_ROTATION_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> ROLLBACK_ROTATION_SCRIPT = new DefaultRedisScript<>();

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
        CONSUME_SCRIPT.setResultType(String.class);
        CONSUME_SCRIPT.setScriptText(
                "local json = redis.call('get', KEYS[1]) " +
                        "if not json then return nil end " +
                        "local ok, record = pcall(cjson.decode, json) " +
                        "if not ok or type(record) ~= 'table' then " +
                        "redis.call('del', KEYS[1]) " +
                        "return nil " +
                        "end " +
                        "if record.familyId and redis.call('exists', KEYS[3] .. record.familyId) == 1 then return nil end " +
                        "if record.state and record.state ~= 'ACTIVE' then return nil end " +
                        "local ttl = redis.call('pttl', KEYS[1]) " +
                        "redis.call('del', KEYS[1]) " +
                        "local revokedAt = ARGV[1] " +
                        "local tombstone = cjson.encode({userId = record.userId, familyId = record.familyId, expiresAt = record.expiresAt, revokedAt = revokedAt, state = 'CONSUMED'}) " +
                        "if ttl and ttl > 0 then redis.call('set', KEYS[2], tombstone, 'px', ttl) end " +
                        "local member = record.refreshToken " +
                        "if not member or member == '' then member = ARGV[2] end " +
                        "if record.familyId and member and member ~= '' then redis.call('srem', KEYS[4] .. record.familyId, member) end " +
                        "return json"
        );
        BEGIN_ROTATION_SCRIPT.setResultType(String.class);
        BEGIN_ROTATION_SCRIPT.setScriptText(
                "local json = redis.call('get', KEYS[1]) " +
                        "if not json then return nil end " +
                        "local ok, record = pcall(cjson.decode, json) " +
                        "if not ok or type(record) ~= 'table' then " +
                        "redis.call('del', KEYS[1]) " +
                        "return nil " +
                        "end " +
                        "if record.familyId and redis.call('exists', KEYS[2] .. record.familyId) == 1 then return nil end " +
                        "if record.state == 'PENDING_ROTATION' and record.pendingExpiresAt and record.pendingExpiresAt <= ARGV[2] then " +
                        "record.state = 'ACTIVE' " +
                        "record.pendingExpiresAt = nil " +
                        "end " +
                        "if record.state and record.state ~= 'ACTIVE' then return nil end " +
                        "record.state = 'PENDING_ROTATION' " +
                        "record.pendingExpiresAt = ARGV[1] " +
                        "local updated = cjson.encode(record) " +
                        "local ttl = redis.call('pttl', KEYS[1]) " +
                        "if ttl and ttl > 0 then redis.call('set', KEYS[1], updated, 'px', ttl) else redis.call('set', KEYS[1], updated) end " +
                        "return updated"
        );
        FINISH_ROTATION_SCRIPT.setResultType(Long.class);
        FINISH_ROTATION_SCRIPT.setScriptText(
                "if redis.call('exists', KEYS[4]) == 1 then return 0 end " +
                        "local json = redis.call('get', KEYS[1]) " +
                        "if not json then return 0 end " +
                        "local ok, record = pcall(cjson.decode, json) " +
                        "if not ok or type(record) ~= 'table' then " +
                        "redis.call('del', KEYS[1]) " +
                        "return 0 " +
                        "end " +
                        "if record.state ~= 'PENDING_ROTATION' then return 0 end " +
                        "if redis.call('exists', KEYS[2]) == 1 then return 0 end " +
                        "local ttl = redis.call('pttl', KEYS[1]) " +
                        "if not ttl or ttl <= 0 then return 0 end " +
                        "local revokedAt = ARGV[4] " +
                        "local oldToken = record.refreshToken " +
                        "if not oldToken or oldToken == '' then oldToken = ARGV[5] end " +
                        "local tombstone = cjson.encode({userId = record.userId, familyId = record.familyId, expiresAt = record.expiresAt, revokedAt = revokedAt, state = 'CONSUMED'}) " +
                        "redis.call('set', 'auth:refresh:revoked:' .. oldToken, tombstone, 'px', ttl) " +
                        "redis.call('set', KEYS[2], ARGV[1], 'EX', ARGV[2]) " +
                        "redis.call('del', KEYS[1]) " +
                        "redis.call('srem', KEYS[3], oldToken) " +
                        "redis.call('sadd', KEYS[3], ARGV[3]) " +
                        "redis.call('expire', KEYS[3], ARGV[2]) " +
                        "return 1"
        );
        ROLLBACK_ROTATION_SCRIPT.setResultType(Long.class);
        ROLLBACK_ROTATION_SCRIPT.setScriptText(
                "local json = redis.call('get', KEYS[1]) " +
                        "if not json then return 0 end " +
                        "local ok, record = pcall(cjson.decode, json) " +
                        "if not ok or type(record) ~= 'table' then " +
                        "redis.call('del', KEYS[1]) " +
                        "return 0 " +
                        "end " +
                        "if record.state ~= 'PENDING_ROTATION' then return 0 end " +
                        "record.state = 'ACTIVE' " +
                        "record.pendingExpiresAt = nil " +
                        "local updated = cjson.encode(record) " +
                        "local ttl = redis.call('pttl', KEYS[1]) " +
                        "if ttl and ttl > 0 then redis.call('set', KEYS[1], updated, 'px', ttl) else redis.call('set', KEYS[1], updated) end " +
                        "return 1"
        );
    }

    private final StringRedisTemplate redisTemplate;
    private final JsonCodec jsonCodec;

    public RedisRefreshTokenRepository(StringRedisTemplate redisTemplate, JsonCodec jsonCodec) {
        this.redisTemplate = redisTemplate;
        this.jsonCodec = jsonCodec;
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

        RedisRefreshRecord record = new RedisRefreshRecord(token, userId, family, expiresAt, "ACTIVE", null);
        try {
            String json = jsonCodec.toJson(record);
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
        } catch (JsonCodecException e) {
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
        return toStoredRefreshToken(readRecord(json), false);
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
        String revokedKey = KEY_PREFIX_TOKEN_REVOKED + token;
        String json = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(KEY_PREFIX_TOKEN + token, revokedKey, KEY_PREFIX_FAMILY_REVOKED, KEY_PREFIX_FAMILY),
                Instant.now().toString(),
                token
        );
        return toStoredRefreshToken(readRecord(json), false);
    }

    @Override
    public StoredRefreshToken beginRotation(String refreshToken, Instant pendingExpiresAt) {
        if (!StringUtils.hasText(refreshToken) || pendingExpiresAt == null) {
            return null;
        }
        String token = refreshToken.trim();
        if (token.isEmpty()) {
            return null;
        }
        String json = redisTemplate.execute(
                BEGIN_ROTATION_SCRIPT,
                List.of(KEY_PREFIX_TOKEN + token, KEY_PREFIX_FAMILY_REVOKED),
                pendingExpiresAt.toString(),
                Instant.now().toString()
        );
        return toStoredRefreshToken(readRecord(json), true);
    }

    @Override
    public boolean finishRotation(
            String pendingRefreshToken,
            String replacementRefreshToken,
            UUID userId,
            String familyId,
            Instant replacementExpiresAt
    ) {
        if (!StringUtils.hasText(pendingRefreshToken)
                || !StringUtils.hasText(replacementRefreshToken)
                || userId == null
                || !StringUtils.hasText(familyId)
                || replacementExpiresAt == null) {
            return false;
        }
        String pendingToken = pendingRefreshToken.trim();
        String replacementToken = replacementRefreshToken.trim();
        String family = familyId.trim();
        if (pendingToken.isEmpty() || replacementToken.isEmpty() || family.isEmpty()) {
            return false;
        }
        long ttlSeconds = Math.max(1, replacementExpiresAt.getEpochSecond() - Instant.now().getEpochSecond());
        RedisRefreshRecord replacement = new RedisRefreshRecord(
                replacementToken,
                userId,
                family,
                replacementExpiresAt,
                "ACTIVE",
                null
        );
        try {
            Long rotated = redisTemplate.execute(
                    FINISH_ROTATION_SCRIPT,
                    List.of(
                            KEY_PREFIX_TOKEN + pendingToken,
                            KEY_PREFIX_TOKEN + replacementToken,
                            KEY_PREFIX_FAMILY + family,
                            KEY_PREFIX_FAMILY_REVOKED + family
                    ),
                    jsonCodec.toJson(replacement),
                    Long.toString(ttlSeconds),
                    replacementToken,
                    Instant.now().toString(),
                    pendingToken
            );
            return rotated != null && rotated > 0;
        } catch (JsonCodecException e) {
            throw new IllegalStateException("refresh token 序列化失败", e);
        }
    }

    @Override
    public boolean rollbackPendingRotation(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return false;
        }
        String token = refreshToken.trim();
        if (token.isEmpty()) {
            return false;
        }
        Long rolledBack = redisTemplate.execute(
                ROLLBACK_ROTATION_SCRIPT,
                List.of(KEY_PREFIX_TOKEN + token)
        );
        return rolledBack != null && rolledBack > 0;
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

    private void writeTombstone(RedisRefreshRecord record, Instant revokedAt) {
        if (record == null || !StringUtils.hasText(record.refreshToken()) || !StringUtils.hasText(record.familyId()) || record.expiresAt() == null) {
            return;
        }
        Instant now = Instant.now();
        if (!record.expiresAt().isAfter(now)) {
            return;
        }
        try {
            String json = jsonCodec.toJson(new Tombstone(record.userId(), record.familyId(), record.expiresAt(), revokedAt));
            long ttlSeconds = Math.max(1, record.expiresAt().getEpochSecond() - now.getEpochSecond());
            redisTemplate.opsForValue().set(KEY_PREFIX_TOKEN_REVOKED + record.refreshToken().trim(), json, ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonCodecException ignored) {
        }
    }

    private RevokedRefreshToken readTombstone(String refreshToken, String json) {
        if (json == null) {
            return null;
        }
        try {
            Tombstone tombstone = jsonCodec.fromJson(json, Tombstone.class);
            return new RevokedRefreshToken(refreshToken, tombstone.userId(), tombstone.familyId(), tombstone.expiresAt(), tombstone.revokedAt());
        } catch (JsonCodecException e) {
            return null;
        }
    }

    private RedisRefreshRecord readRecord(String json) {
        if (json == null) {
            return null;
        }
        try {
            return jsonCodec.fromJson(json, RedisRefreshRecord.class);
        } catch (JsonCodecException e) {
            return null;
        }
    }

    private StoredRefreshToken toStoredRefreshToken(RedisRefreshRecord record, boolean includePending) {
        if (record == null || !StringUtils.hasText(record.refreshToken()) || record.userId() == null || !StringUtils.hasText(record.familyId()) || record.expiresAt() == null) {
            return null;
        }
        String state = StringUtils.hasText(record.state()) ? record.state().trim() : "ACTIVE";
        if (!"ACTIVE".equals(state) && !(includePending && "PENDING_ROTATION".equals(state))) {
            return null;
        }
        return new StoredRefreshToken(record.refreshToken().trim(), record.userId(), record.familyId().trim(), record.expiresAt());
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
        RedisRefreshRecord found = readRecord(redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN + token));
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

    private record RedisRefreshRecord(
            String refreshToken,
            UUID userId,
            String familyId,
            Instant expiresAt,
            String state,
            Instant pendingExpiresAt
    ) {
    }

    private record Tombstone(UUID userId, String familyId, Instant expiresAt, Instant revokedAt) {
    }
}
