package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.PasswordResetTokenRepository;
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
@ConditionalOnProperty(name = "auth.password-reset.store", havingValue = "redis", matchIfMissing = true)
public class RedisPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private static final String KEY_PREFIX = "auth:pwdreset:";
    private static final RedisScript<String> CONSUME_WITH_TTL_SCRIPT = script(
            """
                    local value = redis.call('GET', KEYS[1])
                    if not value then
                        return nil
                    end
                    local ttl = redis.call('PTTL', KEYS[1])
                    redis.call('DEL', KEYS[1])
                    return value .. '|' .. tostring(ttl)
                    """,
            String.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisPasswordResetTokenRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void store(String token, UUID userId, Duration ttl) {
        if (!StringUtils.hasText(token) || userId == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, userId.toString(), ttl);
    }

    @Override
    public ConsumedPasswordResetToken consumeWithTtl(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String value = redisTemplate.execute(CONSUME_WITH_TTL_SCRIPT, List.of(KEY_PREFIX + token.trim()));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String[] parts = value.split("\\|", 2);
        if (parts.length != 2 || !StringUtils.hasText(parts[0])) {
            return null;
        }
        try {
            UUID userId = UUID.fromString(parts[0]);
            long ttlMillis = Long.parseLong(parts[1]);
            return new ConsumedPasswordResetToken(userId, ttlMillis > 0 ? Duration.ofMillis(ttlMillis) : Duration.ZERO);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public UUID consume(String token) {
        ConsumedPasswordResetToken consumed = consumeWithTtl(token);
        return consumed == null ? null : consumed.userId();
    }

    @Override
    public void delete(String token) {
        if (StringUtils.hasText(token)) {
            redisTemplate.delete(KEY_PREFIX + token.trim());
        }
    }

    private static <T> RedisScript<T> script(String scriptText, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(resultType);
        return script;
    }
}
