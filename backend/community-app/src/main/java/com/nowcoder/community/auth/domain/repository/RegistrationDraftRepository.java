package com.nowcoder.community.auth.domain.repository;

import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;

import java.time.Duration;
import java.util.Optional;

public interface RegistrationDraftRepository {

    String issue(PreparedRegistrationDraft draft, Duration ttl);

    Optional<PreparedRegistrationDraft> find(String registrationToken);

    void delete(String registrationToken);
}
