package com.nowcoder.community.auth.domain.model;

import java.util.UUID;

public record PasswordResetToken(String token, UUID userId) {
}
