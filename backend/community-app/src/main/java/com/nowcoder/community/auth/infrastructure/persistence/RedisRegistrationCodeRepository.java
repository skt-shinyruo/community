package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RedisRegistrationCodeRepository implements RegistrationCodeRepository {

    private static final String KEY_PREFIX = "auth:regcode:v2:";

    private static final String REDIS_TIME_LUA = """
            local function redisNowMs()
              local redisTime = redis.call('TIME')
              return (tonumber(redisTime[1]) * 1000) + math.floor(tonumber(redisTime[2]) / 1000)
            end

            """;

    private static final RedisScript<String> ISSUE_SCRIPT = new DefaultRedisScript<>(
            REDIS_TIME_LUA + """
                    local nowMs = redisNowMs()
                    local ttlMs = tonumber(ARGV[2])
                    local cooldownMs = tonumber(ARGV[3])
                    if not ttlMs or ttlMs <= 0 or not cooldownMs or cooldownMs < 0 or ARGV[4] == '' then
                      return 'COOLDOWN_ACTIVE'
                    end

                    local typeReply = redis.call('TYPE', KEYS[1])
                    local keyType = type(typeReply) == 'table' and typeReply.ok or typeReply
                    if keyType ~= 'none' and keyType ~= 'hash' then
                      redis.call('DEL', KEYS[1])
                      keyType = 'none'
                    end

                    if keyType == 'hash' then
                      local values = redis.call('HMGET', KEYS[1],
                        'active_expires_at_ms', 'issued_at_ms', 'state',
                        'replacement_lease_expires_at_ms', 'verification_lease_expires_at_ms',
                        'initial_delivery_lease_expires_at_ms')
                      local activeExpiresAtMs = tonumber(values[1])
                      local issuedAtMs = tonumber(values[2])
                      local state = values[3]
                      local replacementLeaseExpiresAtMs = tonumber(values[4])
                      local verificationLeaseExpiresAtMs = tonumber(values[5])
                      local initialDeliveryLeaseExpiresAtMs = tonumber(values[6])

                      if state == 'PENDING_REPLACEMENT'
                          and replacementLeaseExpiresAtMs and replacementLeaseExpiresAtMs > nowMs then
                        return 'COOLDOWN_ACTIVE'
                      end
                      if state == 'PENDING_VERIFICATION'
                          and verificationLeaseExpiresAtMs and verificationLeaseExpiresAtMs > nowMs then
                        return 'COOLDOWN_ACTIVE'
                      end
                      if state == 'PENDING_INITIAL_DELIVERY'
                          and initialDeliveryLeaseExpiresAtMs and initialDeliveryLeaseExpiresAtMs > nowMs then
                        return 'COOLDOWN_ACTIVE'
                      end
                      if state == 'EXHAUSTED' and activeExpiresAtMs and issuedAtMs
                          and activeExpiresAtMs > nowMs and cooldownMs > 0
                          and (nowMs - issuedAtMs) < cooldownMs then
                        return 'COOLDOWN_ACTIVE'
                      end
                      if state ~= 'ACTIVE' and state ~= 'EXHAUSTED'
                          and state ~= 'PENDING_REPLACEMENT' and state ~= 'PENDING_VERIFICATION'
                          and state ~= 'PENDING_INITIAL_DELIVERY' then
                        return 'COOLDOWN_ACTIVE'
                      end
                      if activeExpiresAtMs and issuedAtMs and activeExpiresAtMs > nowMs
                          and cooldownMs > 0 and (nowMs - issuedAtMs) < cooldownMs then
                        return 'COOLDOWN_ACTIVE'
                      end
                    end

                    local expiresAtMs = nowMs + ttlMs
                    redis.call('DEL', KEYS[1])
                    redis.call('HSET', KEYS[1],
                      'active_code', ARGV[1],
                      'active_delivery_id', ARGV[4],
                      'active_expires_at_ms', tostring(expiresAtMs),
                      'failures', '0',
                      'issued_at_ms', tostring(nowMs),
                      'state', 'ACTIVE')
                    redis.call('PEXPIREAT', KEYS[1], expiresAtMs)
                    return 'ISSUED'
                    """,
            String.class
    );

    private static final RedisScript<String> BEGIN_REPLACEMENT_SCRIPT = new DefaultRedisScript<>(
            REDIS_TIME_LUA + """
                    local nowMs = redisNowMs()
                    local ttlMs = tonumber(ARGV[2])
                    local cooldownMs = tonumber(ARGV[3])
                    local leaseTtlMs = tonumber(ARGV[5])
                    if not ttlMs or ttlMs <= 0 or not cooldownMs or cooldownMs < 0
                        or not leaseTtlMs or leaseTtlMs <= 0 or ARGV[4] == '' then
                      return 'COOLDOWN_ACTIVE'
                    end
                    local leaseExpiresAtMs = nowMs + leaseTtlMs

                    local typeReply = redis.call('TYPE', KEYS[1])
                    local keyType = type(typeReply) == 'table' and typeReply.ok or typeReply
                    if keyType ~= 'none' and keyType ~= 'hash' then
                      redis.call('DEL', KEYS[1])
                      keyType = 'none'
                    end

                    local activeCode = ''
                    local activeExpiresAtMs = 0
                    local failures = 0
                    local issuedAtMs = 0
                    local exhausted = false
                    if keyType == 'hash' then
                      local values = redis.call('HMGET', KEYS[1],
                        'active_code', 'active_expires_at_ms', 'failures', 'issued_at_ms', 'state',
                        'replacement_lease_expires_at_ms', 'verification_lease_expires_at_ms',
                        'initial_delivery_lease_expires_at_ms')
                      activeCode = values[1] or ''
                      activeExpiresAtMs = tonumber(values[2]) or 0
                      failures = tonumber(values[3]) or 0
                      issuedAtMs = tonumber(values[4]) or 0
                      local state = values[5]
                      local replacementLeaseExpiresAtMs = tonumber(values[6])
                      local verificationLeaseExpiresAtMs = tonumber(values[7])
                      local initialDeliveryLeaseExpiresAtMs = tonumber(values[8])

                      if state == 'PENDING_REPLACEMENT'
                          and replacementLeaseExpiresAtMs and replacementLeaseExpiresAtMs > nowMs then
                        return 'COOLDOWN_ACTIVE'
                      end
                      if state == 'PENDING_VERIFICATION'
                          and verificationLeaseExpiresAtMs and verificationLeaseExpiresAtMs > nowMs then
                        return 'COOLDOWN_ACTIVE'
                      end
                      if state == 'PENDING_INITIAL_DELIVERY' then
                        if initialDeliveryLeaseExpiresAtMs and initialDeliveryLeaseExpiresAtMs > nowMs then
                          return 'COOLDOWN_ACTIVE'
                        end
                        redis.call('HSET', KEYS[1], 'state', 'ACTIVE')
                        redis.call('HDEL', KEYS[1],
                          'initial_delivery_id', 'initial_delivery_lease_expires_at_ms')
                        state = 'ACTIVE'
                      end
                      if state == 'EXHAUSTED' then
                        activeCode = ''
                        exhausted = true
                      elseif state ~= 'ACTIVE' and state ~= 'PENDING_REPLACEMENT'
                          and state ~= 'PENDING_VERIFICATION' then
                        return 'COOLDOWN_ACTIVE'
                      end
                    end

                    if exhausted and activeExpiresAtMs > nowMs and cooldownMs > 0
                        and (nowMs - issuedAtMs) < cooldownMs then
                      return 'COOLDOWN_ACTIVE'
                    end
                    if exhausted or activeCode == '' or activeExpiresAtMs <= nowMs then
                      activeCode = ''
                      activeExpiresAtMs = 0
                      failures = 0
                      issuedAtMs = 0
                    elseif cooldownMs > 0 and (nowMs - issuedAtMs) < cooldownMs then
                      return 'COOLDOWN_ACTIVE'
                    end

                    local replacementExpiresAtMs = nowMs + ttlMs
                    redis.call('HSET', KEYS[1],
                      'active_code', activeCode,
                      'active_expires_at_ms', tostring(activeExpiresAtMs),
                      'failures', tostring(failures),
                      'issued_at_ms', tostring(issuedAtMs),
                      'state', 'PENDING_REPLACEMENT',
                      'replacement_code', ARGV[1],
                      'replacement_expires_at_ms', tostring(replacementExpiresAtMs),
                      'replacement_issued_at_ms', tostring(nowMs),
                      'replacement_lease_id', ARGV[4],
                      'replacement_lease_expires_at_ms', tostring(leaseExpiresAtMs))
                    redis.call('HDEL', KEYS[1],
                      'verification_lease_id', 'verification_lease_expires_at_ms',
                      'initial_delivery_id', 'initial_delivery_lease_expires_at_ms')
                    local keyExpiresAtMs = math.max(activeExpiresAtMs, replacementExpiresAtMs, leaseExpiresAtMs)
                    redis.call('PEXPIREAT', KEYS[1], keyExpiresAtMs)
                    return 'ISSUED'
                    """,
            String.class
    );

    private static final RedisScript<Long> PROMOTE_REPLACEMENT_SCRIPT = new DefaultRedisScript<>(
            REDIS_TIME_LUA + """
                    local nowMs = redisNowMs()
                    local minimumValidityMs = tonumber(ARGV[2])
                    if ARGV[1] == '' or not minimumValidityMs or minimumValidityMs < 0 then
                      return 0
                    end
                    local typeReply = redis.call('TYPE', KEYS[1])
                    local keyType = type(typeReply) == 'table' and typeReply.ok or typeReply
                    if keyType ~= 'hash' then
                      return 0
                    end

                    local values = redis.call('HMGET', KEYS[1],
                      'state', 'replacement_code', 'replacement_expires_at_ms',
                      'replacement_issued_at_ms', 'replacement_lease_id',
                      'replacement_lease_expires_at_ms')
                    local replacementExpiresAtMs = tonumber(values[3])
                    local leaseExpiresAtMs = tonumber(values[6])
                    if values[1] ~= 'PENDING_REPLACEMENT' or not values[2] or values[2] == ''
                        or not replacementExpiresAtMs or replacementExpiresAtMs <= nowMs + minimumValidityMs
                        or not values[4] or values[5] ~= ARGV[1]
                        or not leaseExpiresAtMs or leaseExpiresAtMs <= nowMs then
                      return 0
                    end

                    redis.call('HSET', KEYS[1],
                      'active_code', values[2],
                      'active_delivery_id', values[5],
                      'active_expires_at_ms', tostring(replacementExpiresAtMs),
                      'failures', '0',
                      'issued_at_ms', values[4],
                      'state', 'ACTIVE')
                    redis.call('HDEL', KEYS[1],
                      'replacement_code', 'replacement_expires_at_ms', 'replacement_issued_at_ms',
                      'replacement_lease_id', 'replacement_lease_expires_at_ms',
                      'verification_lease_id', 'verification_lease_expires_at_ms',
                      'initial_delivery_id', 'initial_delivery_lease_expires_at_ms')
                    redis.call('PEXPIREAT', KEYS[1], replacementExpiresAtMs)
                    return 1
                    """,
            Long.class
    );

    private static final RedisScript<Long> ABORT_REPLACEMENT_SCRIPT = new DefaultRedisScript<>(
            REDIS_TIME_LUA + """
                    local nowMs = redisNowMs()
                    if ARGV[1] == '' then
                      return 0
                    end
                    local typeReply = redis.call('TYPE', KEYS[1])
                    local keyType = type(typeReply) == 'table' and typeReply.ok or typeReply
                    if keyType ~= 'hash' then
                      return 0
                    end

                    local values = redis.call('HMGET', KEYS[1],
                      'active_code', 'active_expires_at_ms', 'failures', 'issued_at_ms',
                      'state', 'replacement_lease_id')
                    if values[5] ~= 'PENDING_REPLACEMENT' or values[6] ~= ARGV[1] then
                      return 0
                    end

                    local activeExpiresAtMs = tonumber(values[2])
                    if values[1] and values[1] ~= '' and activeExpiresAtMs and activeExpiresAtMs > nowMs then
                      redis.call('HSET', KEYS[1], 'state', 'ACTIVE')
                      redis.call('HDEL', KEYS[1],
                        'replacement_code', 'replacement_expires_at_ms', 'replacement_issued_at_ms',
                        'replacement_lease_id', 'replacement_lease_expires_at_ms',
                        'verification_lease_id', 'verification_lease_expires_at_ms')
                      redis.call('PEXPIREAT', KEYS[1], activeExpiresAtMs)
                    else
                      redis.call('DEL', KEYS[1])
                    end
                    return 1
                    """,
            Long.class
    );

    private static final RedisScript<String> VERIFY_PENDING_SCRIPT = new DefaultRedisScript<>(
            REDIS_TIME_LUA + """
                    local nowMs = redisNowMs()
                    local maxFailures = tonumber(ARGV[2])
                    local leaseTtlMs = tonumber(ARGV[3])
                    local cooldownMs = tonumber(ARGV[5])
                    if not maxFailures or maxFailures <= 0
                        or not leaseTtlMs or leaseTtlMs <= 0 or ARGV[4] == ''
                        or not cooldownMs or cooldownMs < 0 then
                      return 'NOT_FOUND'
                    end
                    local leaseExpiresAtMs = nowMs + leaseTtlMs

                    local typeReply = redis.call('TYPE', KEYS[1])
                    local keyType = type(typeReply) == 'table' and typeReply.ok or typeReply
                    if keyType == 'none' then
                      return 'NOT_FOUND'
                    end
                    if keyType ~= 'hash' then
                      redis.call('DEL', KEYS[1])
                      return 'NOT_FOUND'
                    end

                    local values = redis.call('HMGET', KEYS[1],
                      'active_code', 'active_expires_at_ms', 'failures', 'issued_at_ms', 'state',
                      'replacement_lease_expires_at_ms',
                      'verification_lease_id', 'verification_lease_expires_at_ms',
                      'initial_delivery_id', 'initial_delivery_lease_expires_at_ms')
                    local storedCode = values[1]
                    local expiresAtMs = tonumber(values[2])
                    local failureCount = tonumber(values[3])
                    local issuedAtMs = tonumber(values[4])
                    local state = values[5]

                    if state == 'EXHAUSTED' then
                      return 'TOO_MANY_ATTEMPTS'
                    end

                    if state == 'PENDING_INITIAL_DELIVERY' then
                      local initialDeliveryLeaseExpiresAtMs = tonumber(values[10])
                      if initialDeliveryLeaseExpiresAtMs and initialDeliveryLeaseExpiresAtMs > nowMs then
                        return 'PENDING_CONFLICT'
                      end
                      redis.call('HSET', KEYS[1], 'state', 'ACTIVE')
                      redis.call('HDEL', KEYS[1],
                        'initial_delivery_id', 'initial_delivery_lease_expires_at_ms')
                      state = 'ACTIVE'
                    elseif state == 'PENDING_REPLACEMENT' then
                      local replacementLeaseExpiresAtMs = tonumber(values[6])
                      if replacementLeaseExpiresAtMs and replacementLeaseExpiresAtMs > nowMs then
                        return 'PENDING_CONFLICT'
                      end
                      redis.call('HSET', KEYS[1], 'state', 'ACTIVE')
                      redis.call('HDEL', KEYS[1],
                        'replacement_code', 'replacement_expires_at_ms', 'replacement_issued_at_ms',
                        'replacement_lease_id', 'replacement_lease_expires_at_ms')
                      state = 'ACTIVE'
                    elseif state == 'PENDING_VERIFICATION' then
                      local verificationLeaseExpiresAtMs = tonumber(values[8])
                      if verificationLeaseExpiresAtMs and verificationLeaseExpiresAtMs > nowMs then
                        return 'PENDING_CONFLICT'
                      end
                      redis.call('HSET', KEYS[1], 'state', 'ACTIVE')
                      redis.call('HDEL', KEYS[1],
                        'verification_lease_id', 'verification_lease_expires_at_ms')
                      state = 'ACTIVE'
                    end

                    if state ~= 'ACTIVE' or not storedCode or storedCode == ''
                        or not expiresAtMs or not failureCount or not issuedAtMs then
                      redis.call('DEL', KEYS[1])
                      return 'NOT_FOUND'
                    end
                    if expiresAtMs <= nowMs then
                      redis.call('DEL', KEYS[1])
                      return 'EXPIRED'
                    end

                    if storedCode == ARGV[1] then
                      redis.call('HSET', KEYS[1],
                        'state', 'PENDING_VERIFICATION',
                        'verification_lease_id', ARGV[4],
                        'verification_lease_expires_at_ms', tostring(leaseExpiresAtMs))
                      redis.call('PEXPIREAT', KEYS[1], math.max(expiresAtMs, leaseExpiresAtMs))
                      return 'PENDING'
                    end

                    local nextFailures = failureCount + 1
                    if nextFailures >= maxFailures then
                      local tombstoneExpiresAtMs = math.max(expiresAtMs, nowMs + cooldownMs)
                      redis.call('HSET', KEYS[1],
                        'state', 'EXHAUSTED',
                        'failures', tostring(maxFailures),
                        'issued_at_ms', tostring(nowMs),
                        'active_expires_at_ms', tostring(tombstoneExpiresAtMs))
                      redis.call('HDEL', KEYS[1],
                        'active_code', 'active_delivery_id',
                        'replacement_code', 'replacement_expires_at_ms', 'replacement_issued_at_ms',
                        'replacement_lease_id', 'replacement_lease_expires_at_ms',
                        'verification_lease_id', 'verification_lease_expires_at_ms',
                        'initial_delivery_id', 'initial_delivery_lease_expires_at_ms')
                      redis.call('PEXPIREAT', KEYS[1], tombstoneExpiresAtMs)
                      return 'TOO_MANY_ATTEMPTS'
                    end
                    redis.call('HSET', KEYS[1], 'failures', tostring(nextFailures))
                    redis.call('PEXPIREAT', KEYS[1], expiresAtMs)
                    return 'MISMATCH'
                    """,
            String.class
    );

    private static final RedisScript<Long> PREPARE_MAIL_DELIVERY_SCRIPT = new DefaultRedisScript<>(
            REDIS_TIME_LUA + """
                    local nowMs = redisNowMs()
                    local leaseTtlMs = tonumber(ARGV[4])
                    local minimumValidityMs = tonumber(ARGV[5])
                    if ARGV[1] == '' or ARGV[2] == '' or not leaseTtlMs or leaseTtlMs <= 0
                        or not minimumValidityMs or minimumValidityMs < 0 then
                      return 0
                    end
                    local typeReply = redis.call('TYPE', KEYS[1])
                    local keyType = type(typeReply) == 'table' and typeReply.ok or typeReply
                    if keyType ~= 'hash' then
                      return 0
                    end

                    if ARGV[3] == '' then
                      local values = redis.call('HMGET', KEYS[1],
                        'state', 'active_code', 'active_delivery_id', 'active_expires_at_ms',
                        'initial_delivery_id')
                      local expiresAtMs = tonumber(values[4])
                      if (values[1] ~= 'ACTIVE' and values[1] ~= 'PENDING_INITIAL_DELIVERY')
                          or values[2] ~= ARGV[2] or values[3] ~= ARGV[1]
                          or (values[1] == 'PENDING_INITIAL_DELIVERY' and values[5] ~= ARGV[1])
                          or not expiresAtMs or expiresAtMs <= nowMs + minimumValidityMs then
                        return 0
                      end
                      local leaseExpiresAtMs = nowMs + leaseTtlMs
                      redis.call('HSET', KEYS[1],
                        'state', 'PENDING_INITIAL_DELIVERY',
                        'initial_delivery_id', ARGV[1],
                        'initial_delivery_lease_expires_at_ms', tostring(leaseExpiresAtMs))
                      redis.call('PEXPIREAT', KEYS[1], math.max(expiresAtMs, leaseExpiresAtMs))
                      return 1
                    end

                    local values = redis.call('HMGET', KEYS[1],
                      'state', 'replacement_code', 'replacement_expires_at_ms',
                      'replacement_lease_id', 'active_expires_at_ms')
                    local replacementExpiresAtMs = tonumber(values[3])
                    if values[1] ~= 'PENDING_REPLACEMENT' or values[2] ~= ARGV[2]
                        or values[4] ~= ARGV[3] or ARGV[1] ~= ARGV[3]
                        or not replacementExpiresAtMs
                        or replacementExpiresAtMs <= nowMs + minimumValidityMs then
                      return 0
                    end
                    local leaseExpiresAtMs = nowMs + leaseTtlMs
                    redis.call('HSET', KEYS[1],
                      'replacement_lease_expires_at_ms', tostring(leaseExpiresAtMs))
                    local activeExpiresAtMs = tonumber(values[5]) or 0
                    redis.call('PEXPIREAT', KEYS[1],
                      math.max(activeExpiresAtMs, replacementExpiresAtMs, leaseExpiresAtMs))
                    return 1
                    """,
            Long.class
    );

    private static final RedisScript<Long> COMPLETE_INITIAL_DELIVERY_SCRIPT = new DefaultRedisScript<>(
            REDIS_TIME_LUA + """
                    local nowMs = redisNowMs()
                    local minimumValidityMs = tonumber(ARGV[3])
                    if ARGV[1] == '' or ARGV[2] == ''
                        or not minimumValidityMs or minimumValidityMs < 0 then
                      return 0
                    end
                    local typeReply = redis.call('TYPE', KEYS[1])
                    local keyType = type(typeReply) == 'table' and typeReply.ok or typeReply
                    if keyType ~= 'hash' then
                      return 0
                    end
                    local values = redis.call('HMGET', KEYS[1],
                      'state', 'active_code', 'active_delivery_id', 'active_expires_at_ms',
                      'initial_delivery_id', 'initial_delivery_lease_expires_at_ms')
                    local expiresAtMs = tonumber(values[4])
                    local leaseExpiresAtMs = tonumber(values[6])
                    if values[1] ~= 'PENDING_INITIAL_DELIVERY'
                        or values[2] ~= ARGV[2] or values[3] ~= ARGV[1]
                        or values[5] ~= ARGV[1]
                        or not expiresAtMs or expiresAtMs <= nowMs + minimumValidityMs
                        or not leaseExpiresAtMs or leaseExpiresAtMs <= nowMs then
                      return 0
                    end
                    redis.call('HSET', KEYS[1], 'state', 'ACTIVE')
                    redis.call('HDEL', KEYS[1],
                      'initial_delivery_id', 'initial_delivery_lease_expires_at_ms')
                    redis.call('PEXPIREAT', KEYS[1], expiresAtMs)
                    return 1
                    """,
            Long.class
    );

    private static final RedisScript<Long> CONSUME_PENDING_SCRIPT = new DefaultRedisScript<>(
            REDIS_TIME_LUA + """
                    local nowMs = redisNowMs()
                    if ARGV[1] == '' then
                      return 0
                    end
                    local typeReply = redis.call('TYPE', KEYS[1])
                    local keyType = type(typeReply) == 'table' and typeReply.ok or typeReply
                    if keyType ~= 'hash' then
                      return 0
                    end
                    local values = redis.call('HMGET', KEYS[1],
                      'state', 'verification_lease_id', 'verification_lease_expires_at_ms',
                      'active_code', 'active_expires_at_ms')
                    local leaseExpiresAtMs = tonumber(values[3])
                    local activeExpiresAtMs = tonumber(values[5])
                    if values[1] ~= 'PENDING_VERIFICATION' or values[2] ~= ARGV[1] then
                      return 0
                    end
                    if not leaseExpiresAtMs or leaseExpiresAtMs <= nowMs then
                      if values[4] and values[4] ~= ''
                          and activeExpiresAtMs and activeExpiresAtMs > nowMs then
                        redis.call('HSET', KEYS[1], 'state', 'ACTIVE')
                        redis.call('HDEL', KEYS[1],
                          'verification_lease_id', 'verification_lease_expires_at_ms')
                        redis.call('PEXPIREAT', KEYS[1], activeExpiresAtMs)
                      else
                        redis.call('DEL', KEYS[1])
                      end
                      return 0
                    end
                    redis.call('DEL', KEYS[1])
                    return 1
                    """,
            Long.class
    );

    private static final RedisScript<Long> RESTORE_PENDING_SCRIPT = new DefaultRedisScript<>(
            REDIS_TIME_LUA + """
                    local nowMs = redisNowMs()
                    if ARGV[1] == '' then
                      return 0
                    end
                    local typeReply = redis.call('TYPE', KEYS[1])
                    local keyType = type(typeReply) == 'table' and typeReply.ok or typeReply
                    if keyType ~= 'hash' then
                      return 0
                    end
                    local values = redis.call('HMGET', KEYS[1],
                      'state', 'verification_lease_id', 'active_code', 'active_expires_at_ms')
                    if values[1] ~= 'PENDING_VERIFICATION' or values[2] ~= ARGV[1] then
                      return 0
                    end

                    local activeExpiresAtMs = tonumber(values[4])
                    if values[3] and values[3] ~= '' and activeExpiresAtMs and activeExpiresAtMs > nowMs then
                      redis.call('HSET', KEYS[1], 'state', 'ACTIVE')
                      redis.call('HDEL', KEYS[1],
                        'verification_lease_id', 'verification_lease_expires_at_ms')
                      redis.call('PEXPIREAT', KEYS[1], activeExpiresAtMs)
                    else
                      redis.call('DEL', KEYS[1])
                    end
                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final int maxFailures;
    private final long resendCooldownMillis;
    private final Clock clock;

    public RedisRegistrationCodeRepository(
            StringRedisTemplate redisTemplate,
            RegistrationProperties properties,
            Clock clock
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        RegistrationProperties.Code code = Objects.requireNonNull(
                Objects.requireNonNull(properties, "properties must not be null").getCode(),
                "properties.code must not be null"
        );
        int configured = code.getMaxFailures();
        this.maxFailures = Math.max(1, configured);
        int cooldownSeconds = code.getResendCooldownSeconds();
        this.resendCooldownMillis = Duration.ofSeconds(Math.max(0, cooldownSeconds)).toMillis();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public IssueResult issue(UUID userId, String code, Duration ttl, Duration cooldown, UUID deliveryId) {
        if (!validIssue(userId, code, ttl) || deliveryId == null) {
            return IssueResult.COOLDOWN_ACTIVE;
        }

        String result = redisTemplate.execute(
                ISSUE_SCRIPT,
                keys(userId),
                code.trim(),
                Long.toString(ttl.toMillis()),
                Long.toString(nonNegativeMillis(cooldown)),
                deliveryId.toString()
        );
        return issueResult(result);
    }

    @Override
    public Optional<ReplacementLease> tryBeginReplacement(
            UUID userId,
            String code,
            Duration ttl,
            Duration cooldown,
            Duration leaseTtl
    ) {
        Instant leaseExpiresAt = leaseDeadline(leaseTtl);
        UUID leaseId = UUID.randomUUID();
        if (leaseExpiresAt == null || beginReplacement(
                userId, code, ttl, cooldown, leaseExpiresAt, leaseId) != IssueResult.ISSUED) {
            return Optional.empty();
        }
        return Optional.of(new RedisReplacementLease(userId, leaseId));
    }

    IssueResult beginReplacement(
            UUID userId,
            String code,
            Duration ttl,
            Duration cooldown,
            Instant leaseExpiresAt,
            UUID leaseId
    ) {
        long leaseTtlMs = remainingLeaseMillis(leaseExpiresAt);
        if (!validIssue(userId, code, ttl) || leaseExpiresAt == null || leaseId == null
                || leaseTtlMs <= 0) {
            return IssueResult.COOLDOWN_ACTIVE;
        }

        String result = redisTemplate.execute(
                BEGIN_REPLACEMENT_SCRIPT,
                keys(userId),
                code.trim(),
                Long.toString(ttl.toMillis()),
                Long.toString(nonNegativeMillis(cooldown)),
                leaseId.toString(),
                Long.toString(leaseTtlMs)
        );
        return issueResult(result);
    }

    boolean promoteReplacement(UUID userId, UUID leaseId) {
        return promoteReplacement(userId, leaseId, Duration.ZERO);
    }

    boolean promoteReplacement(UUID userId, UUID leaseId, Duration minimumRemainingValidity) {
        if (userId == null || leaseId == null) {
            return false;
        }
        Long result = redisTemplate.execute(
                PROMOTE_REPLACEMENT_SCRIPT,
                keys(userId),
                leaseId.toString(),
                Long.toString(nonNegativeMillis(minimumRemainingValidity))
        );
        return Long.valueOf(1L).equals(result);
    }

    boolean abortReplacement(UUID userId, UUID leaseId) {
        if (userId == null || leaseId == null) {
            return false;
        }
        Long result = redisTemplate.execute(
                ABORT_REPLACEMENT_SCRIPT,
                keys(userId),
                leaseId.toString()
        );
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public Optional<DeliveryClaim> claimMailDelivery(
            UUID userId,
            UUID deliveryId,
            String code,
            UUID replacementLeaseId,
            Duration leaseTtl,
            Duration minimumRemainingValidity
    ) {
        Instant leaseExpiresAt = leaseDeadline(leaseTtl);
        if (leaseExpiresAt == null || !prepareMailDelivery(
                userId,
                deliveryId,
                code,
                replacementLeaseId,
                leaseExpiresAt,
                minimumRemainingValidity
        )) {
            return Optional.empty();
        }
        return Optional.of(new RedisDeliveryClaim(
                userId,
                deliveryId,
                code.trim(),
                replacementLeaseId,
                leaseTtl,
                minimumRemainingValidity
        ));
    }

    boolean prepareMailDelivery(
            UUID userId,
            UUID deliveryId,
            String code,
            UUID replacementLeaseId,
            Instant leaseExpiresAt
    ) {
        return prepareMailDelivery(
                userId, deliveryId, code, replacementLeaseId, leaseExpiresAt, Duration.ZERO);
    }

    boolean prepareMailDelivery(
            UUID userId,
            UUID deliveryId,
            String code,
            UUID replacementLeaseId,
            Instant leaseExpiresAt,
            Duration minimumRemainingValidity
    ) {
        long leaseTtlMs = remainingLeaseMillis(leaseExpiresAt);
        if (userId == null || deliveryId == null || !StringUtils.hasText(code) || leaseTtlMs <= 0) {
            return false;
        }
        Long result = redisTemplate.execute(
                PREPARE_MAIL_DELIVERY_SCRIPT,
                keys(userId),
                deliveryId.toString(),
                code.trim(),
                replacementLeaseId == null ? "" : replacementLeaseId.toString(),
                Long.toString(leaseTtlMs),
                Long.toString(nonNegativeMillis(minimumRemainingValidity))
        );
        return Long.valueOf(1L).equals(result);
    }

    boolean completeInitialDelivery(
            UUID userId,
            UUID deliveryId,
            String code,
            Duration minimumRemainingValidity
    ) {
        if (userId == null || deliveryId == null || !StringUtils.hasText(code)) {
            return false;
        }
        Long result = redisTemplate.execute(
                COMPLETE_INITIAL_DELIVERY_SCRIPT,
                keys(userId),
                deliveryId.toString(),
                code.trim(),
                Long.toString(nonNegativeMillis(minimumRemainingValidity))
        );
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public VerificationResult claimVerification(
            UUID userId,
            String code,
            Duration leaseTtl
    ) {
        Instant leaseExpiresAt = leaseDeadline(leaseTtl);
        UUID leaseId = UUID.randomUUID();
        VerifyResult result = verifyForConsumption(userId, code, leaseExpiresAt, leaseId);
        if (result == VerifyResult.PENDING) {
            return new RedisVerificationClaim(userId, leaseId);
        }
        return switch (result) {
            case NOT_FOUND -> VerificationFailure.NOT_FOUND;
            case EXPIRED -> VerificationFailure.EXPIRED;
            case MISMATCH -> VerificationFailure.MISMATCH;
            case TOO_MANY_ATTEMPTS -> VerificationFailure.TOO_MANY_ATTEMPTS;
            case PENDING_CONFLICT -> VerificationFailure.PENDING_CONFLICT;
            case PENDING -> throw new IllegalStateException("pending verification must create a claim");
        };
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

    VerifyResult verifyForConsumption(UUID userId, String code, Instant leaseExpiresAt, UUID leaseId) {
        long leaseTtlMs = remainingLeaseMillis(leaseExpiresAt);
        if (userId == null || !StringUtils.hasText(code) || leaseExpiresAt == null || leaseId == null
                || leaseTtlMs <= 0) {
            return VerifyResult.NOT_FOUND;
        }

        String result = redisTemplate.execute(
                VERIFY_PENDING_SCRIPT,
                keys(userId),
                code.trim(),
                Integer.toString(maxFailures),
                Long.toString(leaseTtlMs),
                leaseId.toString(),
                Long.toString(resendCooldownMillis)
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

    boolean consumePending(UUID userId, UUID leaseId) {
        if (userId == null || leaseId == null) {
            return false;
        }
        Long result = redisTemplate.execute(
                CONSUME_PENDING_SCRIPT,
                keys(userId),
                leaseId.toString()
        );
        return Long.valueOf(1L).equals(result);
    }

    boolean restorePending(UUID userId, UUID leaseId) {
        if (userId == null || leaseId == null) {
            return false;
        }
        Long result = redisTemplate.execute(
                RESTORE_PENDING_SCRIPT,
                keys(userId),
                leaseId.toString()
        );
        return Long.valueOf(1L).equals(result);
    }

    private boolean completeMailDelivery(
            UUID userId,
            UUID deliveryId,
            String code,
            UUID replacementLeaseId,
            Duration minimumRemainingValidity
    ) {
        if (replacementLeaseId == null) {
            return completeInitialDelivery(userId, deliveryId, code, minimumRemainingValidity);
        }
        return promoteReplacement(userId, replacementLeaseId, minimumRemainingValidity);
    }

    private Instant leaseDeadline(Duration leaseTtl) {
        if (leaseTtl == null || leaseTtl.isZero() || leaseTtl.isNegative()) {
            return null;
        }
        try {
            return clock.instant().plus(leaseTtl);
        } catch (DateTimeException | ArithmeticException exception) {
            return null;
        }
    }

    private boolean validIssue(UUID userId, String code, Duration ttl) {
        return userId != null && StringUtils.hasText(code) && ttl != null
                && !ttl.isNegative() && !ttl.isZero() && ttl.toMillis() > 0;
    }

    private long nonNegativeMillis(Duration duration) {
        return duration == null ? 0L : Math.max(0L, duration.toMillis());
    }

    private long remainingLeaseMillis(Instant leaseExpiresAt) {
        if (leaseExpiresAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(clock.instant(), leaseExpiresAt).toMillis());
    }

    private IssueResult issueResult(String result) {
        if (!StringUtils.hasText(result)) {
            return IssueResult.COOLDOWN_ACTIVE;
        }
        try {
            return IssueResult.valueOf(result.trim());
        } catch (IllegalArgumentException ex) {
            return IssueResult.COOLDOWN_ACTIVE;
        }
    }

    private final class RedisReplacementLease implements ReplacementLease {

        private final UUID userId;
        private final UUID leaseId;
        private final AtomicBoolean resolved = new AtomicBoolean();

        private RedisReplacementLease(UUID userId, UUID leaseId) {
            this.userId = userId;
            this.leaseId = leaseId;
        }

        @Override
        public UUID id() {
            return leaseId;
        }

        @Override
        public boolean abort() {
            return resolved.compareAndSet(false, true)
                    && abortReplacement(userId, leaseId);
        }
    }

    private final class RedisDeliveryClaim implements DeliveryClaim {

        private final UUID userId;
        private final UUID deliveryId;
        private final String code;
        private final UUID replacementLeaseId;
        private final Duration leaseTtl;
        private final Duration minimumRemainingValidity;
        private final AtomicBoolean completed = new AtomicBoolean();

        private RedisDeliveryClaim(
                UUID userId,
                UUID deliveryId,
                String code,
                UUID replacementLeaseId,
                Duration leaseTtl,
                Duration minimumRemainingValidity
        ) {
            this.userId = userId;
            this.deliveryId = deliveryId;
            this.code = code;
            this.replacementLeaseId = replacementLeaseId;
            this.leaseTtl = leaseTtl;
            this.minimumRemainingValidity = minimumRemainingValidity;
        }

        @Override
        public boolean complete() {
            if (!completed.compareAndSet(false, true)) {
                return false;
            }
            if (completeMailDelivery(
                    userId, deliveryId, code, replacementLeaseId, minimumRemainingValidity)) {
                return true;
            }
            Instant recoveryDeadline = leaseDeadline(leaseTtl);
            return recoveryDeadline != null
                    && prepareMailDelivery(
                            userId,
                            deliveryId,
                            code,
                            replacementLeaseId,
                            recoveryDeadline,
                            minimumRemainingValidity
                    )
                    && completeMailDelivery(
                            userId, deliveryId, code, replacementLeaseId, minimumRemainingValidity);
        }
    }

    private final class RedisVerificationClaim implements VerificationClaim {

        private final UUID userId;
        private final UUID leaseId;
        private final AtomicBoolean resolved = new AtomicBoolean();

        private RedisVerificationClaim(UUID userId, UUID leaseId) {
            this.userId = userId;
            this.leaseId = leaseId;
        }

        @Override
        public boolean consume() {
            return resolved.compareAndSet(false, true)
                    && consumePending(userId, leaseId);
        }

        @Override
        public boolean restore() {
            return resolved.compareAndSet(false, true)
                    && restorePending(userId, leaseId);
        }
    }

    enum VerifyResult {
        NOT_FOUND,
        EXPIRED,
        MISMATCH,
        TOO_MANY_ATTEMPTS,
        PENDING,
        PENDING_CONFLICT
    }

    private String key(UUID userId) {
        return KEY_PREFIX + "{" + userId + "}";
    }

    private List<String> keys(UUID userId) {
        return List.of(key(userId));
    }
}
