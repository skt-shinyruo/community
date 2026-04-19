package com.nowcoder.community.gateway.edge;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "gateway:rate-limit:";
    private static final RedisScript<Long> INCREMENT_WITH_WINDOW_SCRIPT = script(
            """
                    local count = redis.call('incr', KEYS[1])
                    if count == 1 then
                        redis.call('pexpire', KEYS[1], ARGV[1])
                    end
                    return count
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean allow(String key, RateLimitProperties.Policy policy) {
        if (policy == null) {
            return true;
        }
        int limit = Math.max(1, policy.getLimit());
        Duration window = policy.getWindow() == null || policy.getWindow().isZero() || policy.getWindow().isNegative()
                ? Duration.ofMinutes(1)
                : policy.getWindow();
        String redisKey = KEY_PREFIX + key;
        Long count = redisTemplate.execute(
                INCREMENT_WITH_WINDOW_SCRIPT,
                List.of(redisKey),
                Long.toString(Math.max(1L, window.toMillis()))
        );
        if (count == null) {
            throw new IllegalStateException("redis increment returned null");
        }
        return count <= limit;
    }

    private static <T> RedisScript<T> script(String scriptText, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(resultType);
        return script;
    }
}
