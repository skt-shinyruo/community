package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Component
public class RedisLoginRateLimitRepository implements LoginRateLimitRepository {

    private static final String FAILURE_BUDGET_LUA = """
            local function committedFailures(key)
                local raw_count = redis.call('get', key)
                if not raw_count then
                    return 0
                end
                if raw_count ~= '0' and not string.match(raw_count, '^[1-9][0-9]*$') then
                    return nil
                end
                if redis.call('pttl', key) <= 0 then
                    return nil
                end
                if #raw_count > 10 or (#raw_count == 10 and raw_count > '2147483647') then
                    return 2147483647
                end
                local count = tonumber(raw_count)
                if not count or count < 0 or count ~= math.floor(count) then
                    return nil
                end
                return count
            end

            """;
    private static final RedisScript<Long> INCREMENT_WITH_TTL_SCRIPT = script(
            FAILURE_BUDGET_LUA + """
                    local raw_count = redis.call('get', KEYS[1])
                    if raw_count then
                        local current = committedFailures(KEYS[1])
                        if current == nil then
                            return -1
                        end
                        if current >= 2147483647 then
                            return 2147483647
                        end
                    end
                    local count = redis.call('incr', KEYS[1])
                    if count == 1 then
                        redis.call('expire', KEYS[1], ARGV[1])
                    end
                    return count
                    """,
            Long.class
    );
    private static final RedisScript<Long> TRY_ACQUIRE_SCRIPT = script(
            FAILURE_BUDGET_LUA + """
                    local redis_time = redis.call('time')
                    local now_ms = (tonumber(redis_time[1]) * 1000) + math.floor(tonumber(redis_time[2]) / 1000)
                    local failures = committedFailures(KEYS[1])
                    if failures == nil then
                        return -1
                    end
                    redis.call('zremrangebyscore', KEYS[2], '-inf', now_ms)
                    local in_flight = redis.call('zcard', KEYS[2])
                    if failures + in_flight >= tonumber(ARGV[1]) then
                        return 0
                    end
                    redis.call('zadd', KEYS[2], now_ms + tonumber(ARGV[3]), ARGV[2])
                    local latest = redis.call('zrevrange', KEYS[2], 0, 0, 'WITHSCORES')
                    redis.call('pexpireat', KEYS[2], math.floor(tonumber(latest[2])) + 1000)
                    return 1
                    """,
            Long.class
    );
    private static final RedisScript<Long> COUNT_BUDGET_SCRIPT = script(
            FAILURE_BUDGET_LUA + """
                    local redis_time = redis.call('time')
                    local now_ms = (tonumber(redis_time[1]) * 1000) + math.floor(tonumber(redis_time[2]) / 1000)
                    local failures = committedFailures(KEYS[1])
                    if failures == nil then
                        return -1
                    end
                    redis.call('zremrangebyscore', KEYS[2], '-inf', now_ms)
                    return failures + redis.call('zcard', KEYS[2])
                    """,
            Long.class
    );
    private static final RedisScript<Long> RELEASE_SCRIPT = script(
            """
                    redis.call('zrem', KEYS[1], ARGV[1])
                    local count = redis.call('zcard', KEYS[1])
                    if count == 0 then
                        redis.call('del', KEYS[1])
                        return 0
                    end
                    return count
                    """,
            Long.class
    );
    private static final RedisScript<Long> RENEW_SCRIPT = script(
            """
                    local redis_time = redis.call('time')
                    local now_ms = (tonumber(redis_time[1]) * 1000) + math.floor(tonumber(redis_time[2]) / 1000)
                    local expires_at = redis.call('zscore', KEYS[1], ARGV[1])
                    if not expires_at then
                        return 0
                    end
                    if tonumber(expires_at) <= now_ms then
                        redis.call('zrem', KEYS[1], ARGV[1])
                        if redis.call('zcard', KEYS[1]) == 0 then
                            redis.call('del', KEYS[1])
                        end
                        return 0
                    end
                    redis.call('zadd', KEYS[1], 'XX', now_ms + tonumber(ARGV[2]), ARGV[1])
                    local latest = redis.call('zrevrange', KEYS[1], 0, 0, 'WITHSCORES')
                    redis.call('pexpireat', KEYS[1], math.floor(tonumber(latest[2])) + 1000)
                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisLoginRateLimitRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int count(String key) {
        if (!StringUtils.hasText(key)) {
            return 0;
        }
        String value = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            int count = Integer.parseInt(value);
            if (count < 0) {
                throw new IllegalStateException("redis login rate-limit count is negative");
            }
            return count;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("redis login rate-limit count is malformed", e);
        }
    }

    @Override
    public int countBudget(String failureKey, String leaseKey) {
        if (!StringUtils.hasText(failureKey) || !StringUtils.hasText(leaseKey)) {
            return 0;
        }
        Long count = redisTemplate.execute(COUNT_BUDGET_SCRIPT, List.of(failureKey, leaseKey));
        if (count == null) {
            throw new IllegalStateException("redis login failure budget count returned null");
        }
        if (count < 0L) {
            throw new IllegalStateException("redis login failure budget is malformed");
        }
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : count.intValue();
    }

    @Override
    public int increment(String key, int windowSeconds) {
        if (!StringUtils.hasText(key)) {
            return 0;
        }
        Long count = redisTemplate.execute(
                INCREMENT_WITH_TTL_SCRIPT,
                List.of(key),
                Integer.toString(Math.max(1, windowSeconds))
        );
        if (count == null) {
            throw new IllegalStateException("redis login rate-limit increment returned null");
        }
        if (count < 0L) {
            throw new IllegalStateException("redis login rate-limit count is malformed");
        }
        if (count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return count.intValue();
    }

    @Override
    public void delete(String key) {
        if (StringUtils.hasText(key)) {
            redisTemplate.delete(key);
        }
    }

    @Override
    public boolean tryAcquire(
            String failureKey,
            String leaseKey,
            UUID token,
            int limit,
            int leaseMillis
    ) {
        if (!StringUtils.hasText(failureKey) || !StringUtils.hasText(leaseKey)
                || token == null || limit <= 0 || leaseMillis <= 0) {
            return false;
        }
        Long acquired = redisTemplate.execute(
                TRY_ACQUIRE_SCRIPT,
                List.of(failureKey, leaseKey),
                Integer.toString(limit),
                token.toString(),
                Integer.toString(leaseMillis)
        );
        if (acquired == null) {
            throw new IllegalStateException("redis login password-check acquire returned null");
        }
        if (acquired < 0L) {
            throw new IllegalStateException("redis login failure budget is malformed");
        }
        return acquired == 1L;
    }

    @Override
    public boolean renew(String key, UUID token, int leaseMillis) {
        if (!StringUtils.hasText(key) || token == null || leaseMillis <= 0) {
            return false;
        }
        Long renewed = redisTemplate.execute(
                RENEW_SCRIPT,
                List.of(key),
                token.toString(),
                Integer.toString(leaseMillis)
        );
        if (renewed == null) {
            throw new IllegalStateException("redis login password-check renewal returned null");
        }
        return renewed == 1L;
    }

    @Override
    public void release(String key, UUID token) {
        if (!StringUtils.hasText(key) || token == null) {
            return;
        }
        Long remaining = redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token.toString());
        if (remaining == null) {
            throw new IllegalStateException("redis login in-flight release returned null");
        }
    }

    private static <T> RedisScript<T> script(String scriptText, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(resultType);
        return script;
    }
}
