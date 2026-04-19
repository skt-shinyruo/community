package com.nowcoder.community.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(name = "auth.captcha.store", havingValue = "memory")
public class InMemoryCaptchaStore implements CaptchaStore {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Map<String, FailureEntry> failures = new ConcurrentHashMap<>();

    @Override
    public void save(String owner, String code, Duration ttl) {
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(code) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        store.put(owner, new Entry(code, System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public String get(String owner) {
        if (!StringUtils.hasText(owner)) {
            return null;
        }
        Entry entry = store.get(owner);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAtMs) {
            store.remove(owner);
            return null;
        }
        return entry.code;
    }

    @Override
    public VerifyResult verifyAndConsume(String owner, String code) {
        if (!StringUtils.hasText(owner) || !StringUtils.hasText(code)) {
            return VerifyResult.NOT_FOUND;
        }
        AtomicReference<VerifyResult> result = new AtomicReference<>(VerifyResult.NOT_FOUND);
        String trimmedCode = code.trim();
        store.compute(owner, (k, v) -> {
            long now = System.currentTimeMillis();
            if (v == null || now > v.expiresAtMs) {
                result.set(VerifyResult.NOT_FOUND);
                return null;
            }
            if (v.code.equalsIgnoreCase(trimmedCode)) {
                result.set(VerifyResult.MATCHED);
                return null;
            }
            result.set(VerifyResult.MISMATCH);
            return v;
        });
        if (result.get() == VerifyResult.MATCHED) {
            failures.remove(owner);
        }
        return result.get();
    }

    @Override
    public void delete(String owner) {
        if (!StringUtils.hasText(owner)) {
            return;
        }
        store.remove(owner);
        failures.remove(owner);
    }

    private record Entry(String code, long expiresAtMs) {
    }

    @Override
    public int incrementFailures(String owner, Duration ttl) {
        if (!StringUtils.hasText(owner) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return 0;
        }
        FailureEntry updated = failures.compute(owner, (k, v) -> {
            long expiresAtMs = System.currentTimeMillis() + ttl.toMillis();
            if (v == null || System.currentTimeMillis() > v.expiresAtMs) {
                return new FailureEntry(1, expiresAtMs);
            }
            return new FailureEntry(v.count + 1, v.expiresAtMs);
        });
        if (updated == null) {
            return 0;
        }
        if (updated.count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return updated.count;
    }

    private record FailureEntry(int count, long expiresAtMs) {
    }
}
