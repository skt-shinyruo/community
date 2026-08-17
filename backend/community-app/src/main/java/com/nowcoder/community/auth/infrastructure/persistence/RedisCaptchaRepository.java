package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.CaptchaRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class RedisCaptchaRepository implements CaptchaRepository {

    private static final String PREFIX = "captcha:";
    private static final Pattern CAPTCHA_ID_PATTERN = Pattern.compile("\\A[0-9a-f]{32}\\z");
    private static final RedisScript<String> VERIFY_AND_CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
                    local value = redis.call('get', KEYS[1])
                    if not value then
                        return 'NOT_FOUND'
                    end
                    if string.upper(value) == string.upper(ARGV[1]) then
                        redis.call('del', KEYS[1])
                        redis.call('del', KEYS[2])
                        return 'MATCHED'
                    end
                    local failures = redis.call('incr', KEYS[2])
                    if failures == 1 then
                        local remaining = redis.call('pttl', KEYS[1])
                        if remaining <= 0 then
                            remaining = tonumber(ARGV[3])
                        end
                        redis.call('pexpire', KEYS[2], remaining)
                    end
                    if failures >= tonumber(ARGV[2]) then
                        redis.call('del', KEYS[1])
                        redis.call('del', KEYS[2])
                        return 'EXHAUSTED'
                    end
                    return 'MISMATCH'
                    """,
            String.class
    );
    private static final RedisScript<Long> INCREMENT_FAILURES_SCRIPT = new DefaultRedisScript<>(
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

    public RedisCaptchaRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String owner, String code, Duration ttl) {
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(code) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(key(owner), code, ttl);
    }

    @Override
    public boolean verifyAndConsume(String owner, String code, int maxFailures, Duration failureTtl) {
        if (!isCaptchaId(owner) || !StringUtils.hasText(code)
                || maxFailures <= 0 || failureTtl == null || failureTtl.isNegative() || failureTtl.isZero()) {
            return false;
        }
        String result = redisTemplate.execute(
                VERIFY_AND_CONSUME_SCRIPT,
                List.of(key(owner), failKey(owner)),
                code.trim(),
                Integer.toString(maxFailures),
                Long.toString(Math.max(1L, failureTtl.toMillis()))
        );
        if (result == null) {
            throw new IllegalStateException("redis verify captcha returned null");
        }
        return switch (result) {
            case "MATCHED" -> true;
            case "MISMATCH", "EXHAUSTED", "NOT_FOUND" -> false;
            default -> throw new IllegalStateException("unknown captcha verify result");
        };
    }

    @Override
    public int incrementFailures(String owner, Duration ttl) {
        if (!StringUtils.hasText(owner) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return 0;
        }
        Long count = redisTemplate.execute(
                INCREMENT_FAILURES_SCRIPT,
                List.of(failKey(owner)),
                Long.toString(Math.max(1L, ttl.toMillis()))
        );
        if (count == null) {
            throw new IllegalStateException("redis captcha failure increment returned null");
        }
        if (count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return count.intValue();
    }

    private String key(String owner) {
        return PREFIX + "{" + owner + "}:value";
    }

    private String failKey(String owner) {
        return PREFIX + "{" + owner + "}:fail";
    }

    private boolean isCaptchaId(String value) {
        return value != null && CAPTCHA_ID_PATTERN.matcher(value).matches();
    }
}
