package com.nowcoder.community.user.application.result;

import java.util.UUID;

public record PreparedRegistrationUserResult(
        UUID userId,
        String username,
        String email,
        String encodedPassword,
        String headerUrl
) {
}
