package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class RedisLoginRateLimitRepository implements LoginRateLimitRepository {

    private static final RedisScript<Long> INCREMENT_WITH_TTL_SCRIPT = script(
            """
                    local count = redis.call('incr', KEYS[1])
                    if count == 1 then
                        redis.call('expire', KEYS[1], ARGV[1])
                    end
                    return count
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
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
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

    private static <T> RedisScript<T> script(String scriptText, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(resultType);
        return script;
    }
}
