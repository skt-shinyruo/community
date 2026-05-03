package com.nowcoder.community.auth.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "auth.registration.draft.store", havingValue = "redis", matchIfMissing = true)
public class RedisRegistrationDraftRepository implements RegistrationDraftRepository {

    private static final String KEY_PREFIX = "auth:regdraft:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRegistrationDraftRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String issue(PreparedRegistrationDraft draft, Duration ttl) {
        if (draft == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return null;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(draft);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("registration draft serialization failed", ex);
        }

        for (int i = 0; i < 5; i++) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key(token), json, ttl))) {
                return token;
            }
        }
        return null;
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
            return Optional.of(objectMapper.readValue(raw, PreparedRegistrationDraft.class));
        } catch (JsonProcessingException ex) {
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
