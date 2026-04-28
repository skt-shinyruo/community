package com.nowcoder.community.auth.domain.model;

import java.util.UUID;

public record RegistrationCode(UUID userId, String code) {
}
