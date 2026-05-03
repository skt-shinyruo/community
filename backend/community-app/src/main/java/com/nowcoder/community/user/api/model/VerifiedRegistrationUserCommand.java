package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record VerifiedRegistrationUserCommand(
        UUID userId,
        String username,
        String email,
        String encodedPassword,
        String headerUrl
) {
}
