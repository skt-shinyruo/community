package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
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
public class RedisRegistrationCodeRepository implements RegistrationCodeRepository {

    private static final String KEY_PREFIX = "auth:regcode:";
    private static final int ACTIVE_CODE_INDEX = 0;
    private static final int ACTIVE_EXPIRES_INDEX = 1;
    private static final int FAILURES_INDEX = 2;
    private static final int ISSUED_INDEX = 3;
    private static final int STATE_INDEX = 4;
    private static final int PENDING_CODE_INDEX = 5;
    private static final int PENDING_EXPIRES_INDEX = 6;
    private static final int PENDING_ISSUED_INDEX = 7;
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
                      local storedCode, expiresAtMs, failures, issuedAtMs, state = string.match(raw, '^([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)$')
                      if not storedCode then
                        storedCode, expiresAtMs, failures, issuedAtMs = string.match(raw, '^([^|]*)|([^|]*)|([^|]*)|([^|]*)$')
                      end
                      if not storedCode or not expiresAtMs or not failures or not issuedAtMs then
                        redis.call('DEL', KEYS[1])
                        storedCode = nil
                        expiresAtMs = nil
                        failures = nil
                        issuedAtMs = nil
                      end
                      local expires = tonumber(expiresAtMs)
                      local issued = tonumber(issuedAtMs)
                      if expires and issued and expires >= nowMs then
                        if cooldownMs > 0 and (nowMs - issued) < cooldownMs then
                          return 'COOLDOWN_ACTIVE'
                        end
                      end
                    end

                    local payload = ARGV[1] .. '|' .. tostring(nowMs + ttlMs) .. '|0|' .. tostring(nowMs) .. '|ACTIVE|||'
                    redis.call('SET', KEYS[1], payload, 'PX', ttlMs)
                    return 'ISSUED'
                    """,
            String.class
    );
    private static final RedisScript<String> BEGIN_REPLACEMENT_SCRIPT = new DefaultRedisScript<>(
            """
                    local nowMs = tonumber(ARGV[2])
                    local ttlMs = tonumber(ARGV[3])
                    local cooldownMs = tonumber(ARGV[4])
                    if not nowMs or not ttlMs or ttlMs <= 0 or not cooldownMs or cooldownMs < 0 then
                      return 'COOLDOWN_ACTIVE'
                    end

                    local activeCode = ''
                    local activeExpiresAtMs = '0'
                    local failures = '0'
                    local issuedAtMs = '0'
                    local consumeState = 'ACTIVE'
                    local pendingCode = ''
                    local pendingExpiresAtMs = ''
                    local pendingIssuedAtMs = ''
                    local raw = redis.call('GET', KEYS[1])
                    if raw and raw ~= '' then
                      local parts = {}
                      for part in string.gmatch(raw, '([^|]*)') do table.insert(parts, part) end
                      if #parts == 8 then
                        activeCode = parts[1]
                        activeExpiresAtMs = parts[2]
                        failures = parts[3]
                        issuedAtMs = parts[4]
                        consumeState = parts[5]
                        pendingCode = parts[6]
                        pendingExpiresAtMs = parts[7]
                        pendingIssuedAtMs = parts[8]
                      elseif #parts == 5 or #parts == 4 then
                        activeCode = parts[1]
                        activeExpiresAtMs = parts[2]
                        failures = parts[3]
                        issuedAtMs = parts[4]
                        if #parts == 5 then consumeState = parts[5] end
                      else
                        redis.call('DEL', KEYS[1])
                        activeCode = ''
                        activeExpiresAtMs = '0'
                        failures = '0'
                        issuedAtMs = '0'
                        consumeState = 'ACTIVE'
                      end
                      local activeExpires = tonumber(activeExpiresAtMs)
                      local issued = tonumber(issuedAtMs)
                      local pendingIssued = tonumber(pendingIssuedAtMs)
                      if activeExpires and issued and activeExpires >= nowMs then
                        if cooldownMs > 0 and (nowMs - issued) < cooldownMs then
                          return 'COOLDOWN_ACTIVE'
                        end
                      elseif pendingIssued and cooldownMs > 0 and (nowMs - pendingIssued) < cooldownMs then
                        return 'COOLDOWN_ACTIVE'
                      end
                    end

                    local activeExpires = tonumber(activeExpiresAtMs)
                    if not activeExpires or activeExpires < nowMs then
                      activeCode = ''
                      activeExpiresAtMs = '0'
                      failures = '0'
                      issuedAtMs = '0'
                      consumeState = 'ACTIVE'
                    end
                    pendingCode = ARGV[1]
                    pendingExpiresAtMs = tostring(nowMs + ttlMs)
                    pendingIssuedAtMs = tostring(nowMs)
                    local nextState = 'PENDING_REPLACEMENT'
                    local payload = activeCode .. '|' .. activeExpiresAtMs .. '|' .. failures .. '|' .. issuedAtMs .. '|' .. nextState .. '|' .. pendingCode .. '|' .. pendingExpiresAtMs .. '|' .. pendingIssuedAtMs
                    redis.call('SET', KEYS[1], payload, 'PX', ttlMs)
                    return 'ISSUED'
            """,
            String.class
    );
    private static final RedisScript<Long> PROMOTE_REPLACEMENT_SCRIPT = new DefaultRedisScript<>(
            """
                    local promote = 'promote'
                    local raw = redis.call('GET', KEYS[1])
                    if not raw or raw == '' then
                      return 0
                    end
                    local activeCode, activeExpiresAtMs, failures, issuedAtMs, state, pendingCode, pendingExpiresAtMs, pendingIssuedAtMs = string.match(raw, '^([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)$')
                    if not activeCode then
                      return 0
                    end
                    if state ~= 'PENDING_REPLACEMENT' or not pendingCode or pendingCode == '' then
                      return 0
                    end
                    local nowMs = tonumber(ARGV[1])
                    local pendingExpires = tonumber(pendingExpiresAtMs)
                    if not nowMs or not pendingExpires or pendingExpires < nowMs then
                      return 0
                    end
                    local payload = pendingCode .. '|' .. pendingExpiresAtMs .. '|0|' .. pendingIssuedAtMs .. '|ACTIVE|||'
                    redis.call('SET', KEYS[1], payload, 'PX', pendingExpires - nowMs)
                    return 1
                    """,
            Long.class
    );
    private static final RedisScript<Long> ABORT_REPLACEMENT_SCRIPT = new DefaultRedisScript<>(
            """
                    local abort = 'abort'
                    local raw = redis.call('GET', KEYS[1])
                    if not raw or raw == '' then
                      return 0
                    end
                    local activeCode, activeExpiresAtMs, failures, issuedAtMs, state, pendingCode, pendingExpiresAtMs, pendingIssuedAtMs = string.match(raw, '^([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)$')
                    if not activeCode then
                      return 0
                    end
                    if state ~= 'PENDING_REPLACEMENT' then
                      return 0
                    end
                    local nowMs = tonumber(ARGV[1])
                    local activeExpires = tonumber(activeExpiresAtMs)
                    if not nowMs or not activeExpires or activeExpires < nowMs or activeCode == '' then
                      redis.call('DEL', KEYS[1])
                      return 0
                    end
                    local payload = activeCode .. '|' .. activeExpiresAtMs .. '|' .. failures .. '|' .. issuedAtMs .. '|ACTIVE|||'
                    redis.call('SET', KEYS[1], payload, 'PX', activeExpires - nowMs)
                    return 1
                    """,
            Long.class
    );
    private static final RedisScript<String> VERIFY_PENDING_SCRIPT = new DefaultRedisScript<>(
            """
                    local raw = redis.call('GET', KEYS[1])
                    if not raw or raw == '' then
                      return 'NOT_FOUND'
                    end

                    local storedCode, expiresAtMs, failures, issuedAtMs, state, pendingCode, pendingExpiresAtMs, pendingIssuedAtMs = string.match(raw, '^([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)$')
                    if not storedCode then
                      storedCode, expiresAtMs, failures, issuedAtMs, state = string.match(raw, '^([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)$')
                      pendingCode = ''
                      pendingExpiresAtMs = ''
                      pendingIssuedAtMs = ''
                    end
                    if not storedCode then
                      storedCode, expiresAtMs, failures, issuedAtMs = string.match(raw, '^([^|]*)|([^|]*)|([^|]*)|([^|]*)$')
                      state = 'ACTIVE'
                      pendingCode = ''
                      pendingExpiresAtMs = ''
                      pendingIssuedAtMs = ''
                    end
                    if not storedCode or not expiresAtMs or not failures or not issuedAtMs then
                      redis.call('DEL', KEYS[1])
                      return 'NOT_FOUND'
                    end
                    if not state or state == '' then
                      state = 'ACTIVE'
                    end

                    local expires = tonumber(expiresAtMs)
                    local failureCount = tonumber(failures)
                    local issued = tonumber(issuedAtMs)
                    local nowMs = tonumber(ARGV[2])
                    local maxFailures = tonumber(ARGV[3])
                    local pendingTtlMs = tonumber(ARGV[4])
                    if not expires or not failureCount or not issued or not nowMs or not maxFailures or not pendingTtlMs then
                      redis.call('DEL', KEYS[1])
                      return 'NOT_FOUND'
                    end

                    if expires < nowMs then
                      redis.call('DEL', KEYS[1])
                      return 'EXPIRED'
                    end
                    if state == 'PENDING' then
                      return 'PENDING_CONFLICT'
                    end

                    if storedCode == ARGV[1] then
                      local ttl = redis.call('PTTL', KEYS[1])
                      local nextTtl = ttl
                      if pendingTtlMs > 0 and (not ttl or ttl < 0 or ttl > pendingTtlMs) then
                        nextTtl = pendingTtlMs
                      end
                      local nextRaw = storedCode .. '|' .. expiresAtMs .. '|' .. failures .. '|' .. issuedAtMs .. '|PENDING|' .. pendingCode .. '|' .. pendingExpiresAtMs .. '|' .. pendingIssuedAtMs
                      if nextTtl and nextTtl > 0 then
                        redis.call('SET', KEYS[1], nextRaw, 'PX', nextTtl)
                      else
                        redis.call('SET', KEYS[1], nextRaw)
                      end
                      return 'PENDING'
                    end

                    local nextFailures = failureCount + 1
                    if nextFailures >= maxFailures then
                      redis.call('DEL', KEYS[1])
                      return 'TOO_MANY_ATTEMPTS'
                    end

                    local ttl = redis.call('PTTL', KEYS[1])
                    local nextState = state
                    if not nextState or nextState == '' or nextState == 'PENDING' then nextState = 'ACTIVE' end
                    local nextRaw = storedCode .. '|' .. expiresAtMs .. '|' .. tostring(nextFailures) .. '|' .. issuedAtMs .. '|' .. nextState .. '|' .. pendingCode .. '|' .. pendingExpiresAtMs .. '|' .. pendingIssuedAtMs
                    if ttl and ttl > 0 then
                      redis.call('SET', KEYS[1], nextRaw, 'PX', ttl)
                    else
                      redis.call('SET', KEYS[1], nextRaw)
                    end
                    return 'MISMATCH'
                    """,
            String.class
    );
    private static final RedisScript<Long> RESTORE_PENDING_SCRIPT = new DefaultRedisScript<>(
            """
                    local raw = redis.call('GET', KEYS[1])
                    if not raw or raw == '' then
                      return 0
                    end

                    local storedCode, expiresAtMs, failures, issuedAtMs, state, pendingCode, pendingExpiresAtMs, pendingIssuedAtMs = string.match(raw, '^([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)$')
                    if not storedCode then
                      storedCode, expiresAtMs, failures, issuedAtMs, state = string.match(raw, '^([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^|]*)$')
                      pendingCode = ''
                      pendingExpiresAtMs = ''
                      pendingIssuedAtMs = ''
                    end
                    if not storedCode then
                      storedCode, expiresAtMs, failures, issuedAtMs = string.match(raw, '^([^|]*)|([^|]*)|([^|]*)|([^|]*)$')
                      state = 'ACTIVE'
                      pendingCode = ''
                      pendingExpiresAtMs = ''
                      pendingIssuedAtMs = ''
                    end
                    if not storedCode or not expiresAtMs or not failures or not issuedAtMs then
                      redis.call('DEL', KEYS[1])
                      return 0
                    end
                    if state ~= 'PENDING' then
                      return 0
                    end

                    local ttl = redis.call('PTTL', KEYS[1])
                    local nextRaw = storedCode .. '|' .. expiresAtMs .. '|' .. failures .. '|' .. issuedAtMs .. '|ACTIVE|' .. pendingCode .. '|' .. pendingExpiresAtMs .. '|' .. pendingIssuedAtMs
                    if ttl and ttl > 0 then
                      redis.call('SET', KEYS[1], nextRaw, 'PX', ttl)
                    else
                      redis.call('SET', KEYS[1], nextRaw)
                    end
                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final int maxFailures;

    public RedisRegistrationCodeRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.maxFailures = 3;
    }

    @Autowired
    public RedisRegistrationCodeRepository(StringRedisTemplate redisTemplate, RegistrationProperties properties) {
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
    public IssueResult beginReplacement(UUID userId, String code, Duration ttl, Duration cooldown) {
        if (userId == null || !StringUtils.hasText(code) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return IssueResult.COOLDOWN_ACTIVE;
        }

        long ttlMs = ttl.toMillis();
        long cooldownMs = cooldown == null ? 0 : Math.max(0L, cooldown.toMillis());
        String result = redisTemplate.execute(
                BEGIN_REPLACEMENT_SCRIPT,
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
    public void promoteReplacement(UUID userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.execute(PROMOTE_REPLACEMENT_SCRIPT, List.of(key(userId)), Long.toString(System.currentTimeMillis()));
    }

    @Override
    public void abortReplacement(UUID userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.execute(ABORT_REPLACEMENT_SCRIPT, List.of(key(userId)), Long.toString(System.currentTimeMillis()));
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
        VerifyResult result = verifyForConsumption(userId, code, Duration.ZERO);
        if (result == VerifyResult.PENDING) {
            consumePending(userId);
            return VerifyResult.SUCCESS;
        }
        return result;
    }

    @Override
    public VerifyResult verifyForConsumption(UUID userId, String code, Duration pendingTtl) {
        if (userId == null || !StringUtils.hasText(code)) {
            return VerifyResult.NOT_FOUND;
        }

        long pendingTtlMs = pendingTtl == null ? 0 : Math.max(0L, pendingTtl.toMillis());
        String result = redisTemplate.execute(
                VERIFY_PENDING_SCRIPT,
                List.of(key(userId)),
                code.trim(),
                Long.toString(System.currentTimeMillis()),
                Integer.toString(maxFailures),
                Long.toString(pendingTtlMs)
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

    @Override
    public void consumePending(UUID userId) {
        delete(userId);
    }

    @Override
    public void restorePending(UUID userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.execute(RESTORE_PENDING_SCRIPT, List.of(key(userId)));
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }

    private ParsedEntry parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        String[] parts = raw.split("\\|", -1);
        if (parts.length != 4 && parts.length != 5 && parts.length != 8) {
            return null;
        }

        try {
            long expiresAtMs = Long.parseLong(parts[ACTIVE_EXPIRES_INDEX]);
            int failures = Integer.parseInt(parts[FAILURES_INDEX]);
            long issuedAtMs = Long.parseLong(parts[ISSUED_INDEX]);
            return new ParsedEntry(parts[0], expiresAtMs, failures, issuedAtMs);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ParsedEntry(String code, long expiresAtMs, int failures, long issuedAtMs) {
    }
}
