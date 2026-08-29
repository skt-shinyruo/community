package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import com.nowcoder.community.common.json.JsonCodecException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Component
public class RedisRegistrationDraftRepository implements RegistrationDraftRepository {

    private static final String KEY_PREFIX = "auth:regdraft:";
    private static final String ACTIVATED_PREFIX = "ACTIVATED:";

    private static final RedisScript<Long> MARK_ACTIVATED_SCRIPT = new DefaultRedisScript<>(
            """
                    local current = redis.call('GET', KEYS[1])
                    local requestedTtlMs = tonumber(ARGV[2])
                    if not current or ARGV[1] == '' or not requestedTtlMs or requestedTtlMs <= 0 then
                      return 0
                    end
                    local prefix = 'ACTIVATED:'
                    if string.sub(current, 1, string.len(prefix)) == prefix and current ~= ARGV[1] then
                      return 0
                    end
                    local currentTtlMs = redis.call('PTTL', KEYS[1])
                    local retainedTtlMs = requestedTtlMs
                    if currentTtlMs and currentTtlMs > retainedTtlMs then
                      retainedTtlMs = currentTtlMs
                    end
                    redis.call('SET', KEYS[1], ARGV[1], 'PX', retainedTtlMs)
                    return 1
                    """,
            Long.class
    );

    private static final RedisScript<Long> DELETE_PREPARED_SCRIPT = new DefaultRedisScript<>(
            """
                    local current = redis.call('GET', KEYS[1])
                    if not current then
                      return 0
                    end
                    local prefix = 'ACTIVATED:'
                    if string.sub(current, 1, string.len(prefix)) == prefix then
                      return 0
                    end
                    return redis.call('DEL', KEYS[1])
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final JacksonJsonCodec jsonCodec;

    @Autowired
    public RedisRegistrationDraftRepository(
            StringRedisTemplate redisTemplate,
            JacksonJsonCodec jsonCodec
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public boolean store(String registrationToken, PreparedRegistrationDraft draft, Duration ttl) {
        if (!StringUtils.hasText(registrationToken) || draft == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        String json;
        try {
            json = jsonCodec.toJson(draft);
        } catch (JsonCodecException ex) {
            throw new IllegalStateException("registration draft serialization failed", ex);
        }

        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key(registrationToken.trim()), json, ttl));
    }

    @Override
    public Optional<PreparedRegistrationDraft> find(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return Optional.empty();
        }
        String token = registrationToken.trim();
        String raw = redisTemplate.opsForValue().get(key(token));
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        if (raw.startsWith(ACTIVATED_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonCodec.fromJson(raw, PreparedRegistrationDraft.class));
        } catch (JsonCodecException ex) {
            delete(token);
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> findActivatedUserId(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return Optional.empty();
        }
        String raw = redisTemplate.opsForValue().get(key(registrationToken.trim()));
        if (!StringUtils.hasText(raw) || !raw.startsWith(ACTIVATED_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.substring(ACTIVATED_PREFIX.length())));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public boolean markActivated(String registrationToken, UUID userId, Duration ttl) {
        if (!StringUtils.hasText(registrationToken) || userId == null || ttl == null
                || ttl.isNegative() || ttl.isZero() || ttl.toMillis() <= 0) {
            return false;
        }
        Long result = redisTemplate.execute(
                MARK_ACTIVATED_SCRIPT,
                List.of(key(registrationToken.trim())),
                ACTIVATED_PREFIX + userId,
                Long.toString(ttl.toMillis())
        );
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public void delete(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return;
        }
        try {
            redisTemplate.execute(
                    DELETE_PREPARED_SCRIPT,
                    List.of(key(registrationToken.trim()))
            );
        } catch (RuntimeException ignored) {
            // best-effort cleanup
        }
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
