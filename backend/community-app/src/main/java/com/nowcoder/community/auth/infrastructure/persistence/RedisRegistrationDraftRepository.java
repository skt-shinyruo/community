package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "auth.registration.draft.store", havingValue = "redis", matchIfMissing = true)
public class RedisRegistrationDraftRepository implements RegistrationDraftRepository {

    private static final String KEY_PREFIX = "auth:regdraft:";

    private final StringRedisTemplate redisTemplate;
    private final JsonCodec jsonCodec;

    @Autowired
    public RedisRegistrationDraftRepository(
            StringRedisTemplate redisTemplate,
            JsonCodec jsonCodec
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
        try {
            return Optional.of(jsonCodec.fromJson(raw, PreparedRegistrationDraft.class));
        } catch (JsonCodecException ex) {
            delete(token);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return;
        }
        try {
            redisTemplate.delete(key(registrationToken.trim()));
        } catch (RuntimeException ignored) {
            // best-effort cleanup
        }
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
