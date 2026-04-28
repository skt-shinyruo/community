package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.PasswordResetTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "auth.password-reset.store", havingValue = "memory")
public class InMemoryPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void store(String token, UUID userId, Duration ttl) {
        if (!StringUtils.hasText(token) || userId == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        store.put(token.trim(), new Entry(userId, System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public UUID consume(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String key = token.trim();
        Entry entry = store.remove(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAtMs) {
            return null;
        }
        return entry.userId;
    }

    private record Entry(UUID userId, long expiresAtMs) {
    }
}
