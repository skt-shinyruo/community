package com.nowcoder.community.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "auth.password-reset.store", havingValue = "memory")
public class InMemoryPasswordResetTokenStore implements PasswordResetTokenStore {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void store(String token, int userId, Duration ttl) {
        if (!StringUtils.hasText(token) || userId <= 0 || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        store.put(token.trim(), new Entry(userId, System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public Integer consume(String token) {
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
        return entry.userId > 0 ? entry.userId : null;
    }

    private record Entry(int userId, long expiresAtMs) {
    }
}
