package com.nowcoder.community.auth.domain.model;

import java.util.UUID;

public record RegistrationSession(String token, UUID userId) {
}
