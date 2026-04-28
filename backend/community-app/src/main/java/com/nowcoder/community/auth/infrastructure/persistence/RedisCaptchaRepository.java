package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.CaptchaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "auth.captcha.store", havingValue = "redis", matchIfMissing = true)
public class RedisCaptchaRepository implements CaptchaRepository {

    private static final String PREFIX = "captcha:";
    private static final String PREFIX_FAIL = "captcha:fail:";
    private static final RedisScript<String> VERIFY_AND_CONSUME_SCRIPT = script(
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
                    return 'MISMATCH'
                    """,
            String.class
    );
    private static final RedisScript<Long> INCREMENT_FAILURES_SCRIPT = script(
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
    public VerifyResult verifyAndConsume(String owner, String code) {
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(code)) {
            return VerifyResult.NOT_FOUND;
        }
        String result = redisTemplate.execute(
                VERIFY_AND_CONSUME_SCRIPT,
                List.of(key(owner), failKey(owner)),
                code.trim()
        );
        if (result == null) {
            throw new IllegalStateException("redis verify captcha returned null");
        }
        return switch (result) {
            case "MATCHED" -> VerifyResult.MATCHED;
            case "MISMATCH" -> VerifyResult.MISMATCH;
            case "NOT_FOUND" -> VerifyResult.NOT_FOUND;
            default -> throw new IllegalStateException("unknown captcha verify result");
        };
    }

    @Override
    public String get(String owner) {
        if (!StringUtils.hasText(owner)) {
            return null;
        }
        return redisTemplate.opsForValue().get(key(owner));
    }

    @Override
    public void delete(String owner) {
        if (!StringUtils.hasText(owner)) {
            return;
        }
        redisTemplate.delete(key(owner));
        redisTemplate.delete(failKey(owner));
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
        return PREFIX + owner;
    }

    private String failKey(String owner) {
        return PREFIX_FAIL + owner;
    }

    private static <T> RedisScript<T> script(String scriptText, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(resultType);
        return script;
    }
}
