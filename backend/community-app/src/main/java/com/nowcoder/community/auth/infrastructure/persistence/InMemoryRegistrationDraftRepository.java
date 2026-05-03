package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.auth.domain.repository.RegistrationDraftRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "auth.registration.draft.store", havingValue = "memory")
public class InMemoryRegistrationDraftRepository implements RegistrationDraftRepository {

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public String issue(PreparedRegistrationDraft draft, Duration ttl) {
        if (draft == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return null;
        }
        long expiresAtMs = System.currentTimeMillis() + ttl.toMillis();
        for (int i = 0; i < 5; i++) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (store.putIfAbsent(token, new Entry(draft, expiresAtMs)) == null) {
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
        Entry entry = store.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAtMs() < System.currentTimeMillis()) {
            store.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry.draft());
    }

    @Override
    public void delete(String registrationToken) {
        if (StringUtils.hasText(registrationToken)) {
            store.remove(registrationToken.trim());
        }
    }

    private record Entry(PreparedRegistrationDraft draft, long expiresAtMs) {
    }
}
