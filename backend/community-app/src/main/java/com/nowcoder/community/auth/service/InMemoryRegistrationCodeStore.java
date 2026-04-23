package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(name = "auth.registration.code.store", havingValue = "memory")
public class InMemoryRegistrationCodeStore implements RegistrationCodeStore {

    private final Map<UUID, Entry> store = new ConcurrentHashMap<>();
    private final int maxFailures;

    public InMemoryRegistrationCodeStore() {
        this.maxFailures = 3;
    }

    @Autowired
    public InMemoryRegistrationCodeStore(RegistrationProperties properties) {
        int configured = properties == null ? 3 : properties.getCode().getMaxFailures();
        this.maxFailures = Math.max(1, configured);
    }

    @Override
    public void save(UUID userId, String code, Duration ttl) {
        if (userId == null || !StringUtils.hasText(code) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        issue(userId, code, ttl, Duration.ZERO);
    }

    @Override
    public IssueResult issue(UUID userId, String code, Duration ttl, Duration cooldown) {
        if (userId == null || !StringUtils.hasText(code) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return IssueResult.COOLDOWN_ACTIVE;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = cooldown == null ? 0 : Math.max(0L, cooldown.toMillis());
        String normalizedCode = code.trim();
        AtomicReference<IssueResult> result = new AtomicReference<>(IssueResult.ISSUED);
        store.compute(userId, (key, existing) -> {
            if (existing != null && now <= existing.expiresAtMs()) {
                long elapsedMs = now - existing.issuedAtMs();
                if (cooldownMs > 0 && elapsedMs < cooldownMs) {
                    result.set(IssueResult.COOLDOWN_ACTIVE);
                    return existing;
                }
            }
            result.set(IssueResult.ISSUED);
            return new Entry(normalizedCode, now + ttl.toMillis(), 0, now);
        });
        return result.get();
    }

    @Override
    public Long lastSentAtMillis(UUID userId) {
        if (userId == null) {
            return null;
        }

        AtomicReference<Long> result = new AtomicReference<>(null);
        store.computeIfPresent(userId, (key, existing) -> {
            if (System.currentTimeMillis() > existing.expiresAtMs()) {
                return null;
            }
            result.set(existing.issuedAtMs());
            return existing;
        });
        return result.get();
    }

    @Override
    public void delete(UUID userId) {
        if (userId == null) {
            return;
        }
        store.remove(userId);
    }

    @Override
    public VerifyResult verifyAndConsume(UUID userId, String code) {
        if (userId == null || !StringUtils.hasText(code)) {
            return VerifyResult.NOT_FOUND;
        }

        String normalizedCode = code.trim();
        AtomicReference<VerifyResult> result = new AtomicReference<>(VerifyResult.NOT_FOUND);
        store.compute(userId, (key, existing) -> {
            if (existing == null) {
                result.set(VerifyResult.NOT_FOUND);
                return null;
            }

            if (System.currentTimeMillis() > existing.expiresAtMs()) {
                result.set(VerifyResult.EXPIRED);
                return null;
            }

            if (existing.code().equals(normalizedCode)) {
                result.set(VerifyResult.SUCCESS);
                return null;
            }

            int nextFailures = existing.failures() + 1;
            if (nextFailures >= maxFailures) {
                result.set(VerifyResult.TOO_MANY_ATTEMPTS);
                return null;
            }

            result.set(VerifyResult.MISMATCH);
            return new Entry(existing.code(), existing.expiresAtMs(), nextFailures, existing.issuedAtMs());
        });

        return result.get();
    }

    private record Entry(String code, long expiresAtMs, int failures, long issuedAtMs) {
    }
}
