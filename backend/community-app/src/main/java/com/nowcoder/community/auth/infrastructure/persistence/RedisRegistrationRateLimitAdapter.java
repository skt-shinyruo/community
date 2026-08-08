package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.application.port.RegistrationRateLimitPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class RedisRegistrationRateLimitAdapter implements RegistrationRateLimitPort {

    private static final String KEY_PREFIX = "auth:registration:quota:{registration-quota}:";
    private static final Pattern OPAQUE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]+");
    private static final RedisScript<Long> TRY_CONSUME_SCRIPT = script(
            """
                    local window_ms = tonumber(ARGV[1])
                    if not window_ms or window_ms <= 0 or #ARGV ~= (#KEYS + 1) then
                      return -1
                    end

                    -- Validate every bucket before mutating any of them.
                    for i = 1, #KEYS do
                      local maximum = tonumber(ARGV[i + 1])
                      if not maximum or maximum < 1 or maximum ~= math.floor(maximum) then
                        return -1
                      end
                      local raw_count = redis.call('GET', KEYS[i])
                      if raw_count then
                        if raw_count ~= '0' and not string.match(raw_count, '^[1-9][0-9]*$') then
                          return -1
                        end
                        local count = tonumber(raw_count)
                        local ttl_ms = redis.call('PTTL', KEYS[i])
                        if not count or count < 0 or count ~= math.floor(count) or ttl_ms <= 0 then
                          return -1
                        end
                        if count >= maximum then
                          return 0
                        end
                      end
                    end

                    for i = 1, #KEYS do
                      local count = redis.call('INCR', KEYS[i])
                      if count == 1 then
                        redis.call('PEXPIRE', KEYS[i], window_ms)
                      end
                    end
                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisRegistrationRateLimitAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryConsume(Flow flow, Duration window, List<Quota> quotas) {
        if (flow == null || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("registration quota flow/window must be valid");
        }
        if (quotas == null || quotas.isEmpty()) {
            return true;
        }

        long windowMillis = window.toMillis();
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("registration quota window must be at least one millisecond");
        }

        List<String> keys = new ArrayList<>(quotas.size());
        List<String> arguments = new ArrayList<>(quotas.size() + 1);
        arguments.add(Long.toString(windowMillis));
        Set<String> uniqueKeys = new HashSet<>();
        for (Quota quota : quotas) {
            String key = quotaKey(flow, quota);
            if (!uniqueKeys.add(key)) {
                throw new IllegalArgumentException("registration quota buckets must be unique");
            }
            keys.add(key);
            arguments.add(Integer.toString(quota.maximum()));
        }

        Long result = redisTemplate.execute(
                TRY_CONSUME_SCRIPT,
                keys,
                arguments.toArray()
        );
        if (result == null) {
            throw new IllegalStateException("redis registration quota consume returned null");
        }
        if (result < 0L) {
            throw new IllegalStateException("redis registration quota state is malformed");
        }
        if (result > 1L) {
            throw new IllegalStateException("redis registration quota consume returned an unknown result");
        }
        return result == 1L;
    }

    static String quotaKey(Flow flow, Quota quota) {
        if (flow == null || quota == null || quota.dimension() == null
                || !StringUtils.hasText(quota.opaqueIdentifier()) || quota.maximum() <= 0) {
            throw new IllegalArgumentException("registration quota bucket must be valid");
        }
        String opaqueIdentifier = quota.opaqueIdentifier().trim();
        if (!OPAQUE_IDENTIFIER.matcher(opaqueIdentifier).matches()) {
            throw new IllegalArgumentException("registration quota identifier must be opaque base64url text");
        }
        return KEY_PREFIX
                + flow.name().toLowerCase(Locale.ROOT)
                + ":"
                + quota.dimension().name().toLowerCase(Locale.ROOT)
                + ":"
                + opaqueIdentifier;
    }

    private static <T> RedisScript<T> script(String scriptText, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(resultType);
        return script;
    }
}
