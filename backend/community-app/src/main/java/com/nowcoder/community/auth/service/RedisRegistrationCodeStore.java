package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "auth.registration.code.store", havingValue = "redis", matchIfMissing = true)
public class RedisRegistrationCodeStore implements RegistrationCodeStore {

    private static final String KEY_PREFIX = "auth:regcode:";
    private static final RedisScript<String> ISSUE_SCRIPT = new DefaultRedisScript<>(
            """
                    local nowMs = tonumber(ARGV[2])
                    local ttlMs = tonumber(ARGV[3])
                    local cooldownMs = tonumber(ARGV[4])
                    if not nowMs or not ttlMs or ttlMs <= 0 or not cooldownMs or cooldownMs < 0 then
                      return 'COOLDOWN_ACTIVE'
                    end

                    local raw = redis.call('GET', KEYS[1])
                    if raw and raw ~= '' then
                      local storedCode, expiresAtMs, failures, issuedAtMs = string.match(raw, '([^|]*)|([^|]*)|([^|]*)|([^|]*)')
                      if not storedCode or not expiresAtMs or not failures then
                        storedCode, expiresAtMs, failures = string.match(raw, '([^|]*)|([^|]*)|([^|]*)')
                      end
                      local expires = tonumber(expiresAtMs)
                      local issued = tonumber(issuedAtMs)
                      if expires and expires >= nowMs then
                        if not issued then
                          issued = 0
                        end
                        if cooldownMs > 0 and (nowMs - issued) < cooldownMs then
                          return 'COOLDOWN_ACTIVE'
                        end
                      end
                    end

                    local payload = ARGV[1] .. '|' .. tostring(nowMs + ttlMs) .. '|0|' .. tostring(nowMs)
                    redis.call('SET', KEYS[1], payload, 'PX', ttlMs)
                    return 'ISSUED'
                    """,
            String.class
    );
    private static final RedisScript<String> VERIFY_SCRIPT = new DefaultRedisScript<>(
            """
                    local raw = redis.call('GET', KEYS[1])
                    if not raw or raw == '' then
                      return 'NOT_FOUND'
                    end

                    local storedCode, expiresAtMs, failures, issuedAtMs = string.match(raw, '([^|]*)|([^|]*)|([^|]*)|([^|]*)')
                    if not storedCode or not expiresAtMs or not failures then
                      storedCode, expiresAtMs, failures = string.match(raw, '([^|]*)|([^|]*)|([^|]*)')
                    end
                    if not storedCode or not expiresAtMs or not failures then
                      redis.call('DEL', KEYS[1])
                      return 'NOT_FOUND'
                    end

                    local expires = tonumber(expiresAtMs)
                    local failureCount = tonumber(failures)
                    local nowMs = tonumber(ARGV[2])
                    local maxFailures = tonumber(ARGV[3])
                    if not expires or not failureCount or not nowMs or not maxFailures then
                      redis.call('DEL', KEYS[1])
                      return 'NOT_FOUND'
                    end

                    if expires < nowMs then
                      redis.call('DEL', KEYS[1])
                      return 'EXPIRED'
                    end

                    if storedCode == ARGV[1] then
                      redis.call('DEL', KEYS[1])
                      return 'SUCCESS'
                    end

                    local nextFailures = failureCount + 1
                    if nextFailures >= maxFailures then
                      redis.call('DEL', KEYS[1])
                      return 'TOO_MANY_ATTEMPTS'
                    end

                    local ttl = redis.call('PTTL', KEYS[1])
                    local nextIssuedAtMs = issuedAtMs or tostring(nowMs)
                    local nextRaw = storedCode .. '|' .. expiresAtMs .. '|' .. tostring(nextFailures) .. '|' .. nextIssuedAtMs
                    if ttl and ttl > 0 then
                      redis.call('SET', KEYS[1], nextRaw, 'PX', ttl)
                    else
                      redis.call('SET', KEYS[1], nextRaw)
                    end
                    return 'MISMATCH'
                    """,
            String.class
    );

    private final StringRedisTemplate redisTemplate;
    private final int maxFailures;

    public RedisRegistrationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.maxFailures = 3;
    }

    @Autowired
    public RedisRegistrationCodeStore(StringRedisTemplate redisTemplate, RegistrationProperties properties) {
        this.redisTemplate = redisTemplate;
        int configured = properties == null ? 3 : properties.getCode().getMaxFailures();
        this.maxFailures = Math.max(1, configured);
    }

    @Override
    public void save(UUID userId, String code, Duration ttl) {
        if (userId == null || !StringUtils.hasText(code) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        issue(userId, code, ttl, Duration.ZERO);
    }

    @Override
    public IssueResult issue(UUID userId, String code, Duration ttl, Duration cooldown) {
        if (userId == null || !StringUtils.hasText(code) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return IssueResult.COOLDOWN_ACTIVE;
        }

        long ttlMs = ttl.toMillis();
        long cooldownMs = cooldown == null ? 0 : Math.max(0L, cooldown.toMillis());
        String result = redisTemplate.execute(
                ISSUE_SCRIPT,
                List.of(key(userId)),
                code.trim(),
                Long.toString(System.currentTimeMillis()),
                Long.toString(ttlMs),
                Long.toString(cooldownMs)
        );
        if (!StringUtils.hasText(result)) {
            return IssueResult.COOLDOWN_ACTIVE;
        }

        try {
            return IssueResult.valueOf(result.trim());
        } catch (IllegalArgumentException ex) {
            return IssueResult.COOLDOWN_ACTIVE;
        }
    }

    @Override
    public Long lastSentAtMillis(UUID userId) {
        if (userId == null) {
            return null;
        }

        ParsedEntry entry = parse(redisTemplate.opsForValue().get(key(userId)));
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMs() < System.currentTimeMillis()) {
            redisTemplate.delete(key(userId));
            return null;
        }
        return entry.issuedAtMs();
    }

    @Override
    public void delete(UUID userId) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.delete(key(userId));
        } catch (RuntimeException ignored) {
            // best-effort cleanup
        }
    }

    @Override
    public VerifyResult verifyAndConsume(UUID userId, String code) {
        if (userId == null || !StringUtils.hasText(code)) {
            return VerifyResult.NOT_FOUND;
        }

        String result = redisTemplate.execute(
                VERIFY_SCRIPT,
                List.of(key(userId)),
                code.trim(),
                Long.toString(System.currentTimeMillis()),
                Integer.toString(maxFailures)
        );
        if (!StringUtils.hasText(result)) {
            return VerifyResult.NOT_FOUND;
        }

        try {
            return VerifyResult.valueOf(result.trim());
        } catch (IllegalArgumentException ex) {
            return VerifyResult.NOT_FOUND;
        }
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }

    private ParsedEntry parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        String[] parts = raw.split("\\|", -1);
        if (parts.length < 3) {
            return null;
        }

        try {
            long expiresAtMs = Long.parseLong(parts[1]);
            int failures = Integer.parseInt(parts[2]);
            long issuedAtMs = parts.length >= 4 ? Long.parseLong(parts[3]) : expiresAtMs;
            return new ParsedEntry(parts[0], expiresAtMs, failures, issuedAtMs);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ParsedEntry(String code, long expiresAtMs, int failures, long issuedAtMs) {
    }
}
