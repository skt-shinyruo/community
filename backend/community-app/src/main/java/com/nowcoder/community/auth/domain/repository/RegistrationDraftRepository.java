package com.nowcoder.community.auth.domain.repository;

import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationDraftRepository {

    boolean store(String registrationToken, PreparedRegistrationDraft draft, Duration ttl);

    Optional<PreparedRegistrationDraft> find(String registrationToken);

    Optional<UUID> findActivatedUserId(String registrationToken);

    boolean markActivated(String registrationToken, UUID userId, Duration ttl);

    void delete(String registrationToken);
}
