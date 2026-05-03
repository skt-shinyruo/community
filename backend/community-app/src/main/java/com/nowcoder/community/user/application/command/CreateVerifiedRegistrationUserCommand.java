package com.nowcoder.community.user.application.command;

import java.util.UUID;

public record CreateVerifiedRegistrationUserCommand(
        UUID userId,
        String username,
        String encodedPassword,
        String email,
        String headerUrl
) {
}
