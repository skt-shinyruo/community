package com.nowcoder.community.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record PreparedRegistrationDraft(
        UUID userId,
        String username,
        String email,
        String encodedPassword,
        String headerUrl,
        Instant issuedAt,
        Instant expiresAt
) {
}
