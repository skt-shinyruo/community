package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.PasswordResetTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "auth.password-reset.store", havingValue = "redis", matchIfMissing = true)
public class RedisPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private static final String KEY_PREFIX = "auth:pwdreset:{password-reset}:";
    private static final String TOKEN_KEY_PREFIX = KEY_PREFIX + "token:";
    private static final String GENERATION_KEY_PREFIX = KEY_PREFIX + "generation:";

    private static final RedisScript<Long> STORE_SCRIPT = script(
            """
                    if redis.call('GET', KEYS[2]) == 'REVOKED' then
                        return -1
                    end
                    if redis.call('EXISTS', KEYS[1]) == 1 then
                        return 0
                    end
                    redis.call('PSETEX', KEYS[1], ARGV[2], ARGV[1])
                    local generationTtl = redis.call('PTTL', KEYS[2])
                    if generationTtl < tonumber(ARGV[2]) then
                        redis.call('SET', KEYS[2], 'ACTIVE', 'PX', ARGV[2])
                    end
                    return 1
                    """,
            Long.class
    );
    private static final RedisScript<String> BEGIN_CONFIRMATION_SCRIPT = script(
            """
                    local value = redis.call('GET', KEYS[1])
                    if not value then
                        return nil
                    end
                    local userId, version, state, pendingAt, lease = string.match(
                        value,
                        '^([^|]+)|([^|]+)|([^|]+)|([^|]*)|([^|]*)$'
                    )
                    if not userId or not version or not state then
                        redis.call('DEL', KEYS[1])
                        return nil
                    end
                    if redis.call('GET', KEYS[2]) == 'REVOKED' then
                        redis.call('DEL', KEYS[1])
                        return nil
                    end
                    if state == 'PENDING' and pendingAt ~= '' and tonumber(pendingAt) <= tonumber(ARGV[2]) then
                        state = 'ACTIVE'
                        pendingAt = ''
                        lease = ''
                    end
                    if state ~= 'ACTIVE' then
                        return nil
                    end
                    local ttl = redis.call('PTTL', KEYS[1])
                    if not ttl or ttl <= 0 then
                        redis.call('DEL', KEYS[1])
                        return nil
                    end
                    local updated = userId .. '|' .. version .. '|PENDING|' .. ARGV[1] .. '|' .. ARGV[3]
                    redis.call('PSETEX', KEYS[1], ttl, updated)
                    return updated
                    """,
            String.class
    );
    private static final RedisScript<Long> FINISH_CONFIRMATION_SCRIPT = script(
            """
                    local value = redis.call('GET', KEYS[1])
                    if not value then
                        return 0
                    end
                    local userId, version, state, pendingAt, lease = string.match(
                        value,
                        '^([^|]+)|([^|]+)|([^|]+)|([^|]*)|([^|]*)$'
                    )
                    if userId ~= ARGV[1] or version ~= ARGV[2]
                        or state ~= 'PENDING' or lease ~= ARGV[3] then
                        return 0
                    end
                    redis.call('DEL', KEYS[1])
                    return 1
                    """,
            Long.class
    );
    private static final RedisScript<Long> ROLLBACK_CONFIRMATION_SCRIPT = script(
            """
                    local value = redis.call('GET', KEYS[1])
                    if not value then
                        return 0
                    end
                    local userId, version, state, pendingAt, lease = string.match(
                        value,
                        '^([^|]+)|([^|]+)|([^|]+)|([^|]*)|([^|]*)$'
                    )
                    if userId ~= ARGV[1] or version ~= ARGV[2]
                        or state ~= 'PENDING' or lease ~= ARGV[3] then
                        return 0
                    end
                    local ttl = redis.call('PTTL', KEYS[1])
                    if not ttl or ttl <= 0 then
                        redis.call('DEL', KEYS[1])
                        return 0
                    end
                    redis.call('PSETEX', KEYS[1], ttl, userId .. '|' .. version .. '|ACTIVE||')
                    return 1
                    """,
            Long.class
    );
    private static final RedisScript<Long> DELETE_ACTIVE_SCRIPT = script(
            """
                    local value = redis.call('GET', KEYS[1])
                    if not value then
                        return 0
                    end
                    local userId, version, state = string.match(
                        value,
                        '^([^|]+)|([^|]+)|([^|]+)|[^|]*|[^|]*$'
                    )
                    if userId ~= ARGV[1] or version ~= ARGV[2] or state ~= 'ACTIVE' then
                        return 0
                    end
                    redis.call('DEL', KEYS[1])
                    return 1
                    """,
            Long.class
    );
    private static final RedisScript<Long> REVOKE_GENERATION_SCRIPT = script(
            """
                    local ttl = tonumber(ARGV[1])
                    local currentTtl = redis.call('PTTL', KEYS[1])
                    if currentTtl and currentTtl > ttl then
                        ttl = currentTtl
                    end
                    redis.call('SET', KEYS[1], 'REVOKED', 'PX', ttl)
                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisPasswordResetTokenRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void store(String token, UUID userId, long securityVersionAtIssue, Duration ttl) {
        if (!StringUtils.hasText(token)
                || userId == null
                || securityVersionAtIssue < 0L
                || ttl == null
                || ttl.isNegative()
                || ttl.isZero()) {
            return;
        }
        long ttlMillis = Math.max(1L, ttl.toMillis());
        String tokenId = tokenId(token.trim());
        Long stored = redisTemplate.execute(
                STORE_SCRIPT,
                List.of(tokenKey(tokenId), generationKey(userId, securityVersionAtIssue)),
                activeRecord(userId, securityVersionAtIssue),
                Long.toString(ttlMillis)
        );
        if (stored == null || stored == 0L) {
            throw new IllegalStateException("password reset token collision");
        }
        if (stored < 0L) {
            throw new IllegalStateException("password reset token generation is already revoked");
        }
    }

    @Override
    public PendingPasswordResetToken beginConfirmation(
            String token,
            Instant pendingExpiresAt,
            UUID confirmationLeaseId
    ) {
        if (!StringUtils.hasText(token) || pendingExpiresAt == null || confirmationLeaseId == null) {
            return null;
        }
        String normalizedToken = token.trim();
        String tokenKey = tokenKey(tokenId(normalizedToken));
        ResetRecord snapshot = readRecord(redisTemplate.opsForValue().get(tokenKey));
        if (snapshot == null) {
            return null;
        }
        String updated = redisTemplate.execute(
                BEGIN_CONFIRMATION_SCRIPT,
                List.of(tokenKey, generationKey(snapshot.userId(), snapshot.securityVersionAtIssue())),
                Long.toString(pendingExpiresAt.toEpochMilli()),
                Long.toString(Instant.now().toEpochMilli()),
                confirmationLeaseId.toString()
        );
        ResetRecord pending = readRecord(updated);
        if (pending == null || !"PENDING".equals(pending.state())) {
            return null;
        }
        UUID lease = parseUuid(pending.leaseId());
        if (lease == null) {
            return null;
        }
        return new PendingPasswordResetToken(pending.userId(), pending.securityVersionAtIssue(), lease);
    }

    @Override
    public boolean finishConfirmation(
            String token,
            UUID userId,
            long securityVersionAtIssue,
            UUID confirmationLeaseId
    ) {
        if (!validConfirmationArguments(token, userId, securityVersionAtIssue, confirmationLeaseId)) {
            return false;
        }
        Long finished = redisTemplate.execute(
                FINISH_CONFIRMATION_SCRIPT,
                List.of(tokenKey(tokenId(token.trim()))),
                userId.toString(),
                Long.toString(securityVersionAtIssue),
                confirmationLeaseId.toString()
        );
        return finished != null && finished > 0L;
    }

    @Override
    public boolean rollbackConfirmation(
            String token,
            UUID userId,
            long securityVersionAtIssue,
            UUID confirmationLeaseId
    ) {
        if (!validConfirmationArguments(token, userId, securityVersionAtIssue, confirmationLeaseId)) {
            return false;
        }
        Long rolledBack = redisTemplate.execute(
                ROLLBACK_CONFIRMATION_SCRIPT,
                List.of(tokenKey(tokenId(token.trim()))),
                userId.toString(),
                Long.toString(securityVersionAtIssue),
                confirmationLeaseId.toString()
        );
        return rolledBack != null && rolledBack > 0L;
    }

    @Override
    public void delete(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String key = tokenKey(tokenId(token.trim()));
        ResetRecord snapshot = readRecord(redisTemplate.opsForValue().get(key));
        if (snapshot == null) {
            return;
        }
        redisTemplate.execute(
                DELETE_ACTIVE_SCRIPT,
                List.of(key),
                snapshot.userId().toString(),
                Long.toString(snapshot.securityVersionAtIssue())
        );
    }

    @Override
    public void revokeGeneration(UUID userId, long securityVersionAtIssue, Duration minimumTtl) {
        if (userId == null
                || securityVersionAtIssue < 0L
                || minimumTtl == null
                || minimumTtl.isNegative()
                || minimumTtl.isZero()) {
            return;
        }
        redisTemplate.execute(
                REVOKE_GENERATION_SCRIPT,
                List.of(generationKey(userId, securityVersionAtIssue)),
                Long.toString(Math.max(1L, minimumTtl.toMillis()))
        );
    }

    private boolean validConfirmationArguments(
            String token,
            UUID userId,
            long securityVersionAtIssue,
            UUID confirmationLeaseId
    ) {
        return StringUtils.hasText(token)
                && userId != null
                && securityVersionAtIssue >= 0L
                && confirmationLeaseId != null;
    }

    private String activeRecord(UUID userId, long securityVersionAtIssue) {
        return userId + "|" + securityVersionAtIssue + "|ACTIVE||";
    }

    private ResetRecord readRecord(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String[] parts = value.split("\\|", -1);
        if (parts.length != 5) {
            return null;
        }
        try {
            UUID userId = UUID.fromString(parts[0]);
            long securityVersion = Long.parseLong(parts[1]);
            if (securityVersion < 0L || !StringUtils.hasText(parts[2])) {
                return null;
            }
            return new ResetRecord(userId, securityVersion, parts[2], parts[3], parts[4]);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String tokenKey(String tokenId) {
        return TOKEN_KEY_PREFIX + tokenId;
    }

    private String generationKey(UUID userId, long securityVersionAtIssue) {
        return GENERATION_KEY_PREFIX + userId + ":" + securityVersionAtIssue;
    }

    private String tokenId(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static <T> RedisScript<T> script(String scriptText, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(resultType);
        return script;
    }

    private record ResetRecord(
            UUID userId,
            long securityVersionAtIssue,
            String state,
            String pendingExpiresAt,
            String leaseId
    ) {
    }
}
