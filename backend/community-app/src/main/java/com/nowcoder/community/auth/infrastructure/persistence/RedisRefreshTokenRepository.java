package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "auth.refresh.store", havingValue = "redis")
public class RedisRefreshTokenRepository implements RefreshTokenRepository {

    private static final DefaultRedisScript<Long> STORE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<String> BEGIN_ROTATION_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> FINISH_ROTATION_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> ROLLBACK_ROTATION_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> REVOKE_FAMILY_SCRIPT = new DefaultRedisScript<>();

    private static final String KEY_PREFIX = "auth:refresh:{auth-refresh}:";
    private static final String KEY_PREFIX_TOKEN = KEY_PREFIX + "token:";
    private static final String KEY_PREFIX_TOKEN_REVOKED = KEY_PREFIX + "revoked:";
    private static final String KEY_PREFIX_FAMILY = KEY_PREFIX + "family:";
    private static final String KEY_PREFIX_FAMILY_REVOKED = KEY_PREFIX + "family-revoked:";
    private static final String REDIS_TIME_LUA =
            "local function redisNowMs() " +
                    "local redisTime = redis.call('TIME') " +
                    "return (tonumber(redisTime[1]) * 1000) + math.floor(tonumber(redisTime[2]) / 1000) " +
                    "end ";

    static {
        STORE_SCRIPT.setResultType(Long.class);
        STORE_SCRIPT.setScriptText(
                "if redis.call('exists', KEYS[1]) == 1 or redis.call('exists', KEYS[2]) == 1 " +
                        "or redis.call('exists', KEYS[3]) == 1 then return 0 end " +
                        "redis.call('set', KEYS[2], ARGV[1], 'EX', ARGV[2]) " +
                        "redis.call('sadd', KEYS[4], ARGV[3]) " +
                        "local familyTtl = redis.call('ttl', KEYS[4]) " +
                        "if not familyTtl or familyTtl < tonumber(ARGV[2]) then redis.call('expire', KEYS[4], ARGV[2]) end " +
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
                        "if record.securityVersionAtIssue == nil then return nil end " +
                        "if redis.call('exists', KEYS[3]) == 1 then return nil end " +
                        "if record.state ~= 'ACTIVE' then return nil end " +
                        "local ttl = redis.call('pttl', KEYS[1]) " +
                        "redis.call('del', KEYS[1]) " +
                        "local revokedAt = ARGV[1] " +
                        "local tombstone = cjson.encode({userId = record.userId, familyId = record.familyId, securityVersionAtIssue = record.securityVersionAtIssue, expiresAt = record.expiresAt, revokedAt = revokedAt, state = 'CONSUMED'}) " +
                        "if ttl and ttl > 0 then redis.call('set', KEYS[2], tombstone, 'px', ttl) end " +
                        "local member = ARGV[2] " +
                        "if record.familyId and member and member ~= '' then redis.call('srem', KEYS[4], member) end " +
                        "return json"
        );
        BEGIN_ROTATION_SCRIPT.setResultType(String.class);
        BEGIN_ROTATION_SCRIPT.setScriptText(
                REDIS_TIME_LUA +
                        "local nowMs = redisNowMs() " +
                        "local requestedExpiresAtMs = tonumber(ARGV[1]) " +
                        "if not requestedExpiresAtMs or requestedExpiresAtMs <= nowMs then return nil end " +
                        "local json = redis.call('get', KEYS[1]) " +
                        "if not json then return nil end " +
                        "local ok, record = pcall(cjson.decode, json) " +
                        "if not ok or type(record) ~= 'table' then " +
                        "redis.call('del', KEYS[1]) " +
                        "return nil " +
                        "end " +
                        "if record.securityVersionAtIssue == nil then return nil end " +
                        "if redis.call('exists', KEYS[2]) == 1 then return nil end " +
                        "if record.state == 'PENDING_ROTATION' and record.pendingExpiresAtEpochMs and tonumber(record.pendingExpiresAtEpochMs) <= nowMs then " +
                        "record.state = 'ACTIVE' " +
                        "record.pendingExpiresAtEpochMs = nil " +
                        "record.rotationLeaseId = nil " +
                        "end " +
                        "if record.state ~= 'ACTIVE' then return nil end " +
                        "record.state = 'PENDING_ROTATION' " +
                        "record.pendingExpiresAtEpochMs = requestedExpiresAtMs " +
                        "record.rotationLeaseId = ARGV[2] " +
                        "local updated = cjson.encode(record) " +
                        "local ttl = redis.call('pttl', KEYS[1]) " +
                        "if ttl and ttl > 0 then redis.call('set', KEYS[1], updated, 'px', ttl) else redis.call('set', KEYS[1], updated) end " +
                        "return updated"
        );
        FINISH_ROTATION_SCRIPT.setResultType(Long.class);
        FINISH_ROTATION_SCRIPT.setScriptText(
                REDIS_TIME_LUA +
                        "local nowMs = redisNowMs() " +
                        "if redis.call('exists', KEYS[4]) == 1 then return 0 end " +
                        "local json = redis.call('get', KEYS[1]) " +
                        "if not json then return 0 end " +
                        "local ok, record = pcall(cjson.decode, json) " +
                        "if not ok or type(record) ~= 'table' then " +
                        "redis.call('del', KEYS[1]) " +
                        "return 0 " +
                        "end " +
                        "if record.securityVersionAtIssue == nil then return 0 end " +
                        "if record.state ~= 'PENDING_ROTATION' or record.rotationLeaseId ~= ARGV[5] then return 0 end " +
                        "if not record.pendingExpiresAtEpochMs or tonumber(record.pendingExpiresAtEpochMs) <= nowMs then return 0 end " +
                        "if record.tokenId ~= ARGV[6] or record.userId ~= ARGV[7] or record.familyId ~= ARGV[8] " +
                        "or tonumber(record.securityVersionAtIssue) ~= tonumber(ARGV[9]) then return 0 end " +
                        "if redis.call('exists', KEYS[2]) == 1 or redis.call('exists', KEYS[6]) == 1 then return 0 end " +
                        "local ttl = redis.call('pttl', KEYS[1]) " +
                        "if not ttl or ttl <= 0 then return 0 end " +
                        "local revokedAt = ARGV[4] " +
                        "local tombstone = cjson.encode({userId = record.userId, familyId = record.familyId, securityVersionAtIssue = record.securityVersionAtIssue, expiresAt = record.expiresAt, revokedAt = revokedAt, state = 'CONSUMED'}) " +
                        "redis.call('set', KEYS[5], tombstone, 'px', ttl) " +
                        "redis.call('set', KEYS[2], ARGV[1], 'EX', ARGV[2]) " +
                        "redis.call('del', KEYS[1]) " +
                        "redis.call('srem', KEYS[3], ARGV[6]) " +
                        "redis.call('sadd', KEYS[3], ARGV[3]) " +
                        "local familyTtl = redis.call('ttl', KEYS[3]) " +
                        "if not familyTtl or familyTtl < tonumber(ARGV[2]) then redis.call('expire', KEYS[3], ARGV[2]) end " +
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
                        "if record.state ~= 'PENDING_ROTATION' or record.rotationLeaseId ~= ARGV[1] then return 0 end " +
                        "record.state = 'ACTIVE' " +
                        "record.pendingExpiresAtEpochMs = nil " +
                        "record.rotationLeaseId = nil " +
                        "local updated = cjson.encode(record) " +
                        "local ttl = redis.call('pttl', KEYS[1]) " +
                        "if ttl and ttl > 0 then redis.call('set', KEYS[1], updated, 'px', ttl) else redis.call('set', KEYS[1], updated) end " +
                        "return 1"
        );
        REVOKE_SCRIPT.setResultType(Long.class);
        REVOKE_SCRIPT.setScriptText(
                "local json = redis.call('get', KEYS[1]) " +
                        "if not json then return 0 end " +
                        "local ok, record = pcall(cjson.decode, json) " +
                        "if not ok or type(record) ~= 'table' then redis.call('del', KEYS[1]); return 0 end " +
                        "local ttl = redis.call('pttl', KEYS[1]) " +
                        "local tombstone = cjson.encode({userId = record.userId, familyId = record.familyId, securityVersionAtIssue = record.securityVersionAtIssue, expiresAt = record.expiresAt, revokedAt = ARGV[1], state = 'REVOKED'}) " +
                        "if ttl and ttl > 0 then redis.call('set', KEYS[2], tombstone, 'px', ttl) end " +
                        "redis.call('del', KEYS[1]) " +
                        "redis.call('srem', KEYS[3], ARGV[2]) " +
                        "return 1"
        );
        REVOKE_FAMILY_SCRIPT.setResultType(Long.class);
        REVOKE_FAMILY_SCRIPT.setScriptText(
                "local configuredTtlMs = tonumber(ARGV[1]) * 1000 " +
                        "local familyTtlMs = redis.call('pttl', KEYS[1]) " +
                        "local revokedTtlMs = redis.call('pttl', KEYS[2]) " +
                        "if familyTtlMs == -1 or revokedTtlMs == -1 then " +
                        "redis.call('set', KEYS[2], '1') " +
                        "else " +
                        "local ttlMs = configuredTtlMs " +
                        "if familyTtlMs > ttlMs then ttlMs = familyTtlMs end " +
                        "if revokedTtlMs > ttlMs then ttlMs = revokedTtlMs end " +
                        "if ttlMs < 1 then ttlMs = 1 end " +
                        "redis.call('psetex', KEYS[2], ttlMs, '1') " +
                        "end " +
                        "local memberCount = redis.call('scard', KEYS[1]) " +
                        "redis.call('del', KEYS[1]) " +
                        "return memberCount"
        );
    }

    private final StringRedisTemplate redisTemplate;
    private final JsonCodec jsonCodec;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public RedisRefreshTokenRepository(
            StringRedisTemplate redisTemplate,
            JsonCodec jsonCodec,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void store(
            String refreshToken,
            UUID userId,
            String familyId,
            long securityVersionAtIssue,
            Instant expiresAt
    ) {
        if (!StringUtils.hasText(refreshToken)
                || userId == null
                || !StringUtils.hasText(familyId)
                || securityVersionAtIssue < 0
                || expiresAt == null) {
            return;
        }
        String token = refreshToken.trim();
        String family = familyId.trim();
        if (token.isEmpty() || family.isEmpty()) {
            return;
        }

        String tokenId = tokenId(token);
        RedisRefreshRecord record = new RedisRefreshRecord(
                tokenId,
                userId,
                family,
                securityVersionAtIssue,
                expiresAt,
                "ACTIVE",
                null,
                null
        );
        try {
            String json = jsonCodec.toJson(record);
            long ttlSeconds = Math.max(1, expiresAt.getEpochSecond() - clock.instant().getEpochSecond());
            Long stored = redisTemplate.execute(
                    STORE_SCRIPT,
                    List.of(
                            KEY_PREFIX_FAMILY_REVOKED + family,
                            KEY_PREFIX_TOKEN + tokenId,
                            KEY_PREFIX_TOKEN_REVOKED + tokenId,
                            KEY_PREFIX_FAMILY + family
                    ),
                    json,
                    Long.toString(ttlSeconds),
                    tokenId
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
        RedisRefreshRecord record = readRecordForToken(token);
        if (record == null || !StringUtils.hasText(record.familyId())) {
            return null;
        }
        Boolean familyRevoked = redisTemplate.hasKey(KEY_PREFIX_FAMILY_REVOKED + record.familyId().trim());
        return Boolean.TRUE.equals(familyRevoked) ? null : toStoredRefreshToken(token, record, false);
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
        String tokenId = tokenId(token);
        RedisRefreshRecord snapshot = readRecordForToken(token);
        if (snapshot == null || !StringUtils.hasText(snapshot.familyId())) {
            return null;
        }
        String family = snapshot.familyId().trim();
        String revokedKey = KEY_PREFIX_TOKEN_REVOKED + tokenId;
        String json = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(
                        KEY_PREFIX_TOKEN + tokenId,
                        revokedKey,
                        KEY_PREFIX_FAMILY_REVOKED + family,
                        KEY_PREFIX_FAMILY + family
                ),
                clock.instant().toString(),
                tokenId
        );
        return toStoredRefreshToken(token, readRecord(json), false);
    }

    @Override
    public StoredRefreshToken beginRotation(
            String refreshToken,
            Instant pendingExpiresAt,
            UUID rotationLeaseId
    ) {
        if (!StringUtils.hasText(refreshToken) || pendingExpiresAt == null || rotationLeaseId == null) {
            return null;
        }
        String token = refreshToken.trim();
        if (token.isEmpty()) {
            return null;
        }
        String tokenId = tokenId(token);
        RedisRefreshRecord snapshot = readRecordForToken(token);
        if (snapshot == null || !StringUtils.hasText(snapshot.familyId())) {
            return null;
        }
        String json = redisTemplate.execute(
                BEGIN_ROTATION_SCRIPT,
                List.of(
                        KEY_PREFIX_TOKEN + tokenId,
                        KEY_PREFIX_FAMILY_REVOKED + snapshot.familyId().trim()
                ),
                Long.toString(pendingExpiresAt.toEpochMilli()),
                rotationLeaseId.toString()
        );
        return toStoredRefreshToken(token, readRecord(json), true);
    }

    @Override
    public boolean finishRotation(
            String pendingRefreshToken,
            String replacementRefreshToken,
            UUID userId,
            String familyId,
            long securityVersionAtIssue,
            Instant replacementExpiresAt,
            UUID rotationLeaseId
    ) {
        if (!StringUtils.hasText(pendingRefreshToken)
                || !StringUtils.hasText(replacementRefreshToken)
                || userId == null
                || !StringUtils.hasText(familyId)
                || securityVersionAtIssue < 0
                || replacementExpiresAt == null
                || rotationLeaseId == null) {
            return false;
        }
        String pendingToken = pendingRefreshToken.trim();
        String replacementToken = replacementRefreshToken.trim();
        String family = familyId.trim();
        if (pendingToken.isEmpty() || replacementToken.isEmpty() || family.isEmpty()) {
            return false;
        }
        String pendingTokenId = tokenId(pendingToken);
        String replacementTokenId = tokenId(replacementToken);
        long ttlSeconds = Math.max(1, replacementExpiresAt.getEpochSecond() - clock.instant().getEpochSecond());
        RedisRefreshRecord replacement = new RedisRefreshRecord(
                replacementTokenId,
                userId,
                family,
                securityVersionAtIssue,
                replacementExpiresAt,
                "ACTIVE",
                null,
                null
        );
        try {
            Long rotated = redisTemplate.execute(
                    FINISH_ROTATION_SCRIPT,
                    List.of(
                            KEY_PREFIX_TOKEN + pendingTokenId,
                            KEY_PREFIX_TOKEN + replacementTokenId,
                            KEY_PREFIX_FAMILY + family,
                            KEY_PREFIX_FAMILY_REVOKED + family,
                            KEY_PREFIX_TOKEN_REVOKED + pendingTokenId,
                            KEY_PREFIX_TOKEN_REVOKED + replacementTokenId
                    ),
                    jsonCodec.toJson(replacement),
                    Long.toString(ttlSeconds),
                    replacementTokenId,
                    clock.instant().toString(),
                    rotationLeaseId.toString(),
                    pendingTokenId,
                    userId.toString(),
                    family,
                    Long.toString(securityVersionAtIssue)
            );
            return rotated != null && rotated > 0;
        } catch (JsonCodecException e) {
            throw new IllegalStateException("refresh token 序列化失败", e);
        }
    }

    @Override
    public boolean rollbackPendingRotation(String refreshToken, UUID rotationLeaseId) {
        if (!StringUtils.hasText(refreshToken) || rotationLeaseId == null) {
            return false;
        }
        String token = refreshToken.trim();
        if (token.isEmpty()) {
            return false;
        }
        Long rolledBack = redisTemplate.execute(
                ROLLBACK_ROTATION_SCRIPT,
                List.of(KEY_PREFIX_TOKEN + tokenId(token)),
                rotationLeaseId.toString()
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
        return readTombstone(token, redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN_REVOKED + tokenId(token)));
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

    private RedisRefreshRecord readRecordForToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        String token = refreshToken.trim();
        return token.isEmpty()
                ? null
                : readRecord(redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN + tokenId(token)));
    }

    private Tombstone readTombstoneValue(String json) {
        if (json == null) {
            return null;
        }
        try {
            return jsonCodec.fromJson(json, Tombstone.class);
        } catch (JsonCodecException e) {
            return null;
        }
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String tokenId(String token) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private StoredRefreshToken toStoredRefreshToken(
            String refreshToken,
            RedisRefreshRecord record,
            boolean includePending
    ) {
        if (record == null
                || record.userId() == null
                || !StringUtils.hasText(record.familyId())
                || record.securityVersionAtIssue() == null
                || record.expiresAt() == null) {
            return null;
        }
        String state = record.state();
        if (!"ACTIVE".equals(state) && !(includePending && "PENDING_ROTATION".equals(state))) {
            return null;
        }
        return new StoredRefreshToken(
                refreshToken,
                record.userId(),
                record.familyId().trim(),
                record.securityVersionAtIssue(),
                record.expiresAt(),
                parseUuid(record.rotationLeaseId())
        );
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
        String tokenId = tokenId(token);
        RedisRefreshRecord found = readRecord(redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN + tokenId));
        if (found == null || !StringUtils.hasText(found.familyId())) {
            return;
        }
        redisTemplate.execute(
                REVOKE_SCRIPT,
                List.of(
                        KEY_PREFIX_TOKEN + tokenId,
                        KEY_PREFIX_TOKEN_REVOKED + tokenId,
                        KEY_PREFIX_FAMILY + found.familyId().trim()
                ),
                clock.instant().toString(),
                tokenId
        );
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

        redisTemplate.execute(
                REVOKE_FAMILY_SCRIPT,
                List.of(familyKey, revokedKey),
                Long.toString(Math.max(1L, jwtProperties.getRefreshTokenTtlSeconds()))
        );
    }

    @Override
    public boolean revokeFamilyByPresentedToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return false;
        }
        String token = refreshToken.trim();
        if (token.isEmpty()) {
            return false;
        }
        String tokenId = tokenId(token);
        RedisRefreshRecord active = readRecord(redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN + tokenId));
        Tombstone revoked = readTombstoneValue(
                redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN_REVOKED + tokenId)
        );
        String familyId = active != null ? active.familyId() : revoked == null ? null : revoked.familyId();
        if (!StringUtils.hasText(familyId)) {
            return false;
        }
        revokeFamily(familyId);
        return true;
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        return 0;
    }

    private record RedisRefreshRecord(
            String tokenId,
            UUID userId,
            String familyId,
            Long securityVersionAtIssue,
            Instant expiresAt,
            String state,
            Long pendingExpiresAtEpochMs,
            String rotationLeaseId
    ) {
    }

    private record Tombstone(
            UUID userId,
            String familyId,
            Long securityVersionAtIssue,
            Instant expiresAt,
            Instant revokedAt
    ) {
    }
}
