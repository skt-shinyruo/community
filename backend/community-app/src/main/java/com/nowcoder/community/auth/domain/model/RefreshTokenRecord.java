package com.nowcoder.community.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenRecord(String refreshToken, UUID userId, String familyId, Instant expiresAt) {
}
