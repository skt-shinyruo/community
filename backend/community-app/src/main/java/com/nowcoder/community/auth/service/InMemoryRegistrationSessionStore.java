package com.nowcoder.community.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "auth.registration.session.store", havingValue = "memory")
public class InMemoryRegistrationSessionStore implements RegistrationSessionStore {

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public String issue(int userId, Duration ttl) {
        if (userId <= 0 || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return null;
        }

        long expiresAtMs = System.currentTimeMillis() + ttl.toMillis();
        // UUID without hyphens => 32 hex chars.
        for (int i = 0; i < 5; i++) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (store.putIfAbsent(token, new Entry(userId, expiresAtMs)) == null) {
                return token;
            }
        }
        return null;
    }

    @Override
    public Integer findUserId(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return null;
        }

        String token = registrationToken.trim();
        Entry entry = store.get(token);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMs < System.currentTimeMillis()) {
            store.remove(token);
            return null;
        }
        return entry.userId;
    }

    @Override
    public void delete(String registrationToken) {
        if (!StringUtils.hasText(registrationToken)) {
            return;
        }
        store.remove(registrationToken.trim());
    }

    private static final class Entry {
        private final int userId;
        private final long expiresAtMs;

        private Entry(int userId, long expiresAtMs) {
            this.userId = userId;
            this.expiresAtMs = expiresAtMs;
        }
    }
}

